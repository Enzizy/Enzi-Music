//! Google Cast output shared by the desktop and Android players.

use std::net::{IpAddr, SocketAddr};
use std::sync::atomic::{AtomicI32, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use innertube::SongItem;
use oxicast::{
    Capability, CastApp, CastClient, DeviceInfo, Image, MediaInfo, MediaMetadata, MediaStatus,
};
use serde::Serialize;

use crate::{audioproxy::CastRelay, orchestrator::PlaybackData};

const NO_MEDIA: i32 = -1;

#[derive(Clone, Serialize)]
pub struct CastTarget {
    pub id: String,
    pub name: String,
}

#[derive(Default)]
pub struct CastOutput {
    devices: Mutex<Vec<DeviceInfo>>,
    discovery: tokio::sync::Mutex<()>,
    active: Mutex<Option<Arc<CastSession>>>,
    pub change: tokio::sync::Mutex<()>,
}

impl CastOutput {
    pub fn session(&self) -> Option<Arc<CastSession>> {
        self.active.lock().unwrap().clone()
    }

    pub fn set_session(&self, session: Arc<CastSession>) {
        *self.active.lock().unwrap() = Some(session);
    }

    pub fn remove(&self, expected: &Arc<CastSession>) -> bool {
        let mut active = self.active.lock().unwrap();
        if !active.as_ref().is_some_and(|session| Arc::ptr_eq(session, expected)) {
            return false;
        }
        *active = None;
        true
    }

    pub async fn discover(&self, _app: &tauri::AppHandle) -> Result<Vec<CastTarget>, String> {
        let _scan = self.discovery.lock().await;
        #[cfg(target_os = "android")]
        crate::media::cast_discovery(_app, true)?;
        let found = oxicast::discovery::discover_devices(Duration::from_secs(3)).await;
        #[cfg(target_os = "android")]
        let _ = crate::media::cast_discovery(_app, false);
        let mut devices = found.map_err(|e| format!("Cast discovery failed: {e}"))?;
        devices.retain(|d| is_local(d.ip) && d.capabilities.contains(&Capability::AudioOutput));
        devices.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
        let targets = devices.iter().map(target).collect();
        *self.devices.lock().unwrap() = devices;
        Ok(targets)
    }

    pub async fn connect(&self, id: &str) -> Result<Arc<CastSession>, String> {
        let device = self
            .devices
            .lock()
            .unwrap()
            .iter()
            .find(|d| target(d).id == id)
            .cloned()
            .ok_or("This Cast device is unavailable. Search again.")?;
        let client = CastClient::builder(device.ip.to_string(), device.port)
            .auto_reconnect(false)
            .connect_timeout(Duration::from_secs(5))
            .connect()
            .await
            .map_err(|e| format!("Could not reach {}: {e}", device.name))?;
        let application = match client.launch_app(&CastApp::DefaultMediaReceiver).await {
            Ok(application) => application,
            Err(error) => {
                let _ = client.close().await;
                return Err(format!("Could not open Cast on {}: {error}", device.name));
            }
        };
        Ok(Arc::new(CastSession {
            target: target(&device),
            peer: SocketAddr::new(device.ip, device.port),
            client,
            application: application.session_id,
            media: AtomicI32::new(NO_MEDIA),
            relay: tokio::sync::Mutex::new(None),
        }))
    }
}

fn is_local(ip: IpAddr) -> bool {
    match ip {
        IpAddr::V4(ip) => ip.is_private() || ip.is_link_local(),
        IpAddr::V6(ip) => ip.segments()[0] & 0xfe00 == 0xfc00,
    }
}

fn target(device: &DeviceInfo) -> CastTarget {
    CastTarget {
        id: device
            .uuid
            .clone()
            .unwrap_or_else(|| SocketAddr::new(device.ip, device.port).to_string()),
        name: device.name.clone(),
    }
}

pub struct CastSession {
    pub target: CastTarget,
    pub client: CastClient,
    pub application: String,
    peer: SocketAddr,
    media: AtomicI32,
    relay: tokio::sync::Mutex<Option<CastRelay>>,
}

impl CastSession {
    pub fn has_media(&self) -> bool {
        self.media.load(Ordering::Acquire) != NO_MEDIA
    }

    pub fn owns(&self, media_session: i32) -> bool {
        media_session != NO_MEDIA && self.media.load(Ordering::Acquire) == media_session
    }

    pub fn finish(&self, media_session: i32) -> bool {
        media_session != NO_MEDIA
            && self
                .media
                .compare_exchange(media_session, NO_MEDIA, Ordering::AcqRel, Ordering::Acquire)
                .is_ok()
    }

    pub async fn load(
        &self,
        data: &PlaybackData,
        item: &SongItem,
        position: f64,
        playing: bool,
    ) -> Result<MediaStatus, String> {
        let mut active_relay = self.relay.lock().await;
        let relay = CastRelay::open(self.peer, data).await?;
        let images = item
            .thumbnail
            .iter()
            .filter(|url| url.starts_with("https://"))
            .map(|url| Image { url: url.clone(), width: None, height: None })
            .collect();
        let media =
            MediaInfo::new(&relay.url, &relay.content_type).metadata(MediaMetadata::MusicTrack {
                title: Some(item.title.clone()),
                artist: Some(item.artists.clone()),
                album_name: item.album.clone(),
                composer: None,
                track_number: None,
                disc_number: None,
                images,
            });
        let status = self
            .client
            .load_media(&media, playing, position, None)
            .await
            .map_err(|e| format!("{} could not play this track: {e}", self.target.name))?;
        if status.idle_reason == Some(oxicast::IdleReason::Error) {
            return Err(format!("{} could not play this track.", self.target.name));
        }
        self.media.store(status.media_session_id, Ordering::Release);
        *active_relay = Some(relay);
        Ok(status)
    }

    pub async fn clear_media(&self) {
        let mut relay = self.relay.lock().await;
        if self.media.swap(NO_MEDIA, Ordering::AcqRel) != NO_MEDIA {
            let _ = self.client.stop_media().await;
        }
        relay.take();
    }

    pub async fn close(&self, stop: bool) {
        let mut relay = self.relay.lock().await;
        if stop && self.has_media() {
            let _ = self.client.disconnect().await;
        } else {
            let _ = self.client.close().await;
        }
        self.media.store(NO_MEDIA, Ordering::Release);
        relay.take();
    }
}
