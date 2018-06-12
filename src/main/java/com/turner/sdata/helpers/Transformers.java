package com.turner.sdata.helpers;

public class Transformers {
	private String name;
	private boolean ownQueue;
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public boolean isOwnQueue() {
		return ownQueue;
	}
	public void setOwnQueue(boolean ownQueue) {
		this.ownQueue = ownQueue;
	}
	
	@Override
	public int hashCode() {
		
		return this.name.hashCode()+(ownQueue?1:0);
	}
	@Override
	public boolean equals(Object obj) {
		if(obj==null || !(obj instanceof Transformers))
			return false;
		Transformers t=(Transformers)obj;
		
		if(this.name.equals(t) && this.ownQueue==t.ownQueue)
			return true;
		else
			return false;
	}
	
	
}
