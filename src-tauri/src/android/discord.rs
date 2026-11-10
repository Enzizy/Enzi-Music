//! Android stand-in for Discord Rich Presence: there is no Discord client to talk to on a phone.

use innertube::SongItem;

#[derive(Debug, Clone, Default)]
pub struct RpcConfig;

impl RpcConfig {
    pub fn parse(_json: Option<&str>) -> Self {
        RpcConfig
    }
}

pub struct DiscordHandle;

impl DiscordHandle {
    pub fn set_track(&self, _item: &SongItem) {}
    pub fn set_album(&self, _video_id: &str, _album: &str) {}
    pub fn set_duration(&self, _secs: f64, _speed: f64) {}
    pub fn set_position(&self, _pos: f64, _speed: f64) {}
    pub fn set_playing(&self, _playing: bool) {}
    pub fn set_enabled(&self, _on: bool) {}
    pub fn set_config(&self, _cfg: RpcConfig) {}
}

pub fn spawn(_enabled: bool, _cfg: RpcConfig) -> Option<DiscordHandle> {
    None
}
