package io.pivotal.rsocketserver.data;

import java.io.Serializable;

import org.springframework.messaging.rsocket.RSocketRequester;

public class Notification implements Serializable{
    private String source;
    private String destination;
    private String text;
    private String clientid;
    
    
   
	/**
	 * @return the clientid
	 */
	public String getClientid() {
		return clientid;
	}

	/**
	 * @param clientid the clientid to set
	 */
	public void setClientid(String clientid) {
		this.clientid = clientid;
	}

	public Notification(String source, String destination, String text, String clientid) {
        this.source = source;
        this.destination = destination;
        this.text = text;        
        this.clientid = clientid;
    }

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    public String getText() {
        return text;
    }

    @Override
	public String toString() {
		return "Notification [source=" + source + ", destination=" + destination + ", text=" + text + ", clientid="
				+ clientid + "]";
	}

}
