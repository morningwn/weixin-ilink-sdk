package io.github.morningwn.protocol;

/**
 * Protocol constants from iLink Bot API.
 */
public final class ProtocolValues {

    /** Message type: user inbound message. */
    public static final int MESSAGE_TYPE_USER = 1;
    /** Message type: bot outbound message. */
    public static final int MESSAGE_TYPE_BOT = 2;

    /** Message state: new. */
    public static final int MESSAGE_STATE_NEW = 0;
    /** Message state: generating. */
    public static final int MESSAGE_STATE_GENERATING = 1;
    /** Message state: finish. */
    public static final int MESSAGE_STATE_FINISH = 2;

    /** Message item type: text. */
    public static final int ITEM_TYPE_TEXT = 1;
    /** Message item type: image. */
    public static final int ITEM_TYPE_IMAGE = 2;
    /** Message item type: voice. */
    public static final int ITEM_TYPE_VOICE = 3;
    /** Message item type: file. */
    public static final int ITEM_TYPE_FILE = 4;
    /** Message item type: video. */
    public static final int ITEM_TYPE_VIDEO = 5;

    /** Typing status: start/keep typing. */
    public static final int TYPING_STATUS_START = 1;
    /** Typing status: stop typing. */
    public static final int TYPING_STATUS_STOP = 2;

    /** Business success code. */
    public static final int RET_OK = 0;
    /** Session expired code. */
    public static final int RET_SESSION_EXPIRED = -14;

    /** QR status: waiting. */
    public static final String QR_STATUS_WAIT = "wait";
    /** QR status: scanned. */
    public static final String QR_STATUS_SCANED = "scaned";
    /** QR status: scanned and redirect required. */
    public static final String QR_STATUS_SCANED_BUT_REDIRECT = "scaned_but_redirect";
    /** QR status: confirmed. */
    public static final String QR_STATUS_CONFIRMED = "confirmed";
    /** QR status: expired. */
    public static final String QR_STATUS_EXPIRED = "expired";

    private ProtocolValues() {
    }
}
