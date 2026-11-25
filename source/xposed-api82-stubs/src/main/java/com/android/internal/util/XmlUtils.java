package com.android.internal.util;

import org.xmlpull.v1.XmlPullParserException;

import java.io.InputStream;
import java.util.Map;

/**
 * Compile-only stub for a hidden Android framework API. Never packaged: the platform provides the
 * real class at runtime.
 */
public class XmlUtils {
    public static Map<String, String> readMapXml(InputStream in) throws XmlPullParserException {
        throw new UnsupportedOperationException("stub");
    }
}
