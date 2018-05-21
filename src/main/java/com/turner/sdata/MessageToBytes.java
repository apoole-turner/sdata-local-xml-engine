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

@Description("")
@Name("")

public class MessageToBytes implements TransformIF {


	@Override
	public Message transform(Message message) throws PluginException {


		GenericMessage newMessage = null;
		try {

			GenericMessage gm = GenericMessageFactory.createGenericMessage(message);
			newMessage = GenericMessageFactory.createGenericMessage();
			newMessage.setBytesProperty("data", SerializationUtils.serialize(gm));
			newMessage.setStringProperty("nextAction", gm.getStringProperty("nextAction"));
			newMessage.setStringProperty("currentAction", "workflowInjector");

		} catch (JMSException e) {
			throw new RuntimeException("message didn't contain  property", e);
		} catch (Exception e) {
			throw new PluginException("Generic Message could not be generated", e);
		} 
		return newMessage;
	}

}