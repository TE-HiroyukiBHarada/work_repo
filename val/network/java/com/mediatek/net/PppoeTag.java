package com.mediatek.net;


/**
*PPPoETag
* The value of Pppoe package.
*
* -------------------------
*       Type         |      Length
* -------------------------
*                    Value
* -------------------------
*@hide
*/
public class PppoeTag {

    public final static int PPPOETAG_END_OF_LIST        = 0x0000;
    public final static int PPPOETAG_SERVICE_NAME       = 0x0101;
    public final static int PPPOETAG_AC_NAME            = 0x0102;
    public final static int PPPOETAG_HOST_UNIQ          = 0x0103;
    public final static int PPPOETAG_AC_COOKIE          = 0x0104;
    public final static int PPPOETAG_VENDOR_SPECIFIC    = 0x0105;
    public final static int PPPOETAG_RELAY_SESSION_ID   = 0x0110;
    public final static int PPPOETAG_SERVICE_NAME_ERROR = 0x0201;
    public final static int PPPOETAG_AC_SYSTEM_ERROR    = 0x0202;
    public final static int PPPOETAG_GENERIC_ERROR      = 0x0203;

    public int mType;
    public int mLength;
    public String mValue;

    public PppoeTag() {
        mType   = PPPOETAG_END_OF_LIST;
        mLength = 0;
        mValue  = "";
    }
    public PppoeTag(int type, String value){
        mType   = type;
        mLength = value.length();
        mValue  = value;
    }

    public PppoeTag[] newArray(int size) {
            return new PppoeTag[size];
    }
    
}



