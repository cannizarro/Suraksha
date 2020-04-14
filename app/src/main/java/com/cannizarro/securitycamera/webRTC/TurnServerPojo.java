
package com.cannizarro.securitycamera.webRTC;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class TurnServerPojo {

    @Expose
    public String s;

    @SerializedName("v")
    @Expose
    public IceServerList iceServerList;

    public class IceServerList {

        @SerializedName("iceServers")
        @Expose
        public List<IceServer> iceServers = null;

    }

}
