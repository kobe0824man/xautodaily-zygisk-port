package com.tencent.mobileqq.vashealth.websso;

import com.tencent.mobileqq.pb.ByteStringMicro;
import com.tencent.mobileqq.pb.MessageMicro;
import com.tencent.mobileqq.pb.PBBytesField;
import com.tencent.mobileqq.pb.PBField;
import com.tencent.mobileqq.pb.PBInt64Field;
import com.tencent.mobileqq.pb.PBStringField;

public final class WebSSOAgent {
    public static final class UniSsoServerReqComm extends MessageMicro<UniSsoServerReqComm> {
        static final MessageMicro.FieldMap __fieldMap__ = MessageMicro.initFieldMap(new int[]{8, 18, 26}, new String[]{"platform", "osver", "mqqver"}, new Object[]{0L, "", ""}, UniSsoServerReqComm.class);
        public final PBInt64Field platform = PBField.initInt64(0);
        public final PBStringField osver = PBField.initString("");
        public final PBStringField mqqver = PBField.initString("");
    }

    public static final class UniSsoServerReq extends MessageMicro<UniSsoServerReq> {
        static final MessageMicro.FieldMap __fieldMap__ = MessageMicro.initFieldMap(new int[]{10, 18, 26}, new String[]{"comm", "reqdata", "pbReqData"}, new Object[]{null, "", ByteStringMicro.EMPTY}, UniSsoServerReq.class);
        public UniSsoServerReqComm comm = new UniSsoServerReqComm();
        public final PBStringField reqdata = PBField.initString("");
        public final PBBytesField pbReqData = PBField.initBytes(ByteStringMicro.EMPTY);
    }
}