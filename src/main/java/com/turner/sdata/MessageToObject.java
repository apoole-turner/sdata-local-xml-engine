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
import com.turner.loki.XmlWorkflowEngine;
import com.turner.loki.annotations.Description;
import com.turner.loki.annotations.Name;
import com.turner.loki.core.exceptions.PluginException;
import com.turner.loki.core.interfaces.TransformIF;
import com.turner.loki.core.interfaces.ValidateIF;

public class MessageToObject implements ValidateIF {
	Logger logger = LoggerFactory.getLogger(MessageToObject.class);


	@Override
	public void validate(Message message) throws PluginException {
		GenericMessage newMessage = null;
		try {
			GenericMessage gm = GenericMessageFactory.createGenericMessage(message);

			byte[] objBytes = gm.getBytesProperty("body");
			Object obj = SerializationUtils.deserialize(objBytes);
			if (!(obj instanceof GenericMessage)) {
				throw new PluginException("The deserialized object is not a genericMessage");
			}
			newMessage =  GenericMessageFactory.createGenericMessage((GenericMessage) obj);
			newMessage.setStringProperty("nextAction", newMessage.getStringProperty("nextActionToBeCalled"));
			newMessage.setStringProperty("currentAction", "workflowInjector");

			XmlWorkflowEngine xmle = XmlWorkflowEngine.getInstance();
			xmle.receive(newMessage);

		} catch (JMSException e) {
			throw new RuntimeException("message didn't contain  property", e);
		} catch (Exception e) {
			throw new PluginException("Generic Message could not be generated", e);
		}
		
	}

}