// Android gets the phone layout: bottom tabs instead of the titlebar and sidebar, a compact player
// above them, and a full-screen now playing. Decided once, from the user agent, because a phone
// never turns into a desktop and the two layouts differ in structure, not just in width.
export const mobile = typeof navigator !== 'undefined' && /Android/i.test(navigator.userAgent);
