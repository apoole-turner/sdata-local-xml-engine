package com.turner.sdata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;

import javax.jms.JMSException;
import javax.jms.Message;

import org.apache.commons.lang3.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.turner.loki.GenericMessage;
import com.turner.loki.GenericMessageFactory;
import com.turner.loki.annotations.Description;
import com.turner.loki.annotations.Name;
import com.turner.loki.core.exceptions.PluginException;
import com.turner.loki.core.interfaces.TransformIF;

@Description("")
@Name("")

public class MessageBytesPlugin implements TransformIF {
	Logger logger = LoggerFactory.getLogger(MessageBytesPlugin.class);
	private static enum ByteConversion{
		BYTES,OBJECT;
	}
	@Override
	public Message transform(Message message) throws PluginException {
	
		ByteArrayOutputStream bos = new ByteArrayOutputStream();

		GenericMessage newMessage = null;
		try {
			
			GenericMessage gm = GenericMessageFactory.createGenericMessage(message);
			ByteConversion bc=ByteConversion.valueOf(gm.getStringProperty("to"));
			if(bc==ByteConversion.BYTES) {
				newMessage = GenericMessageFactory.createGenericMessage();
				newMessage.setBytesProperty("data", SerializationUtils.serialize(gm));
			}else if(bc==ByteConversion.OBJECT) {
				byte[] objBytes=gm.getBytesProperty("data");
				Object obj=SerializationUtils.deserialize(objBytes);
				if(!(obj instanceof GenericMessage)) {
					throw new PluginException("The deserialized object is not a genericMessage");
				}
				newMessage=(GenericMessage)obj;
			}

		} catch (JMSException e) {
			throw new RuntimeException("message didn't contain  property", e);
		} catch (Exception e) {
			throw new PluginException("Generic Message could not be generated", e);
		} finally {
			try {
				bos.close();
			} catch (IOException ex) {
				// ignore close exception
			}
		}

		return newMessage;
	}
	
}