//! Android stand-in for the PoToken generator. BotGuard runs on V8, which has no Android build, so
//! no token is ever minted here and the orchestrator falls through to the clients that need none.

use std::sync::Arc;
use std::time::Duration;

use crate::db::Db;

pub struct PoTokenGenerator;

impl PoTokenGenerator {
    pub fn new(_db: Arc<Db>) -> Self {
        PoTokenGenerator
    }

    pub async fn invalidate_session_token(&self) {}

    pub async fn get_session_po_token(&self, _visitor_data: &str) -> Option<String> {
        None
    }

    pub async fn get_streaming_po_token(
        &self,
        _video_id: &str,
        _visitor_data: &str,
    ) -> Option<String> {
        None
    }

    pub async fn prewarm(&self, _visitor_data: &str) {}

    pub async fn teardown_if_idle(&self, _idle: Duration) {}
}
