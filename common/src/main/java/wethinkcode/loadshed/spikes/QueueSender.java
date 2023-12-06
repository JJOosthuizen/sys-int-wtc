package wethinkcode.loadshed.spikes;

import javax.jms.*;

import org.apache.activemq.ActiveMQConnectionFactory;

/**
 * I am a small "maker" app for receiving MQ messages from the Stage Service.
 */
public class QueueSender implements Runnable {
    private static long NAP_TIME = 2000; // ms

    public static final String MQ_URL = "tcp://localhost:61616";

    public static final String MQ_USER = "admin";

    public static final String MQ_PASSWD = "admin";

    public static final String MQ_QUEUE_NAME = "stage";

    public static void main(String[] args) {
        final QueueSender app = new QueueSender();
        app.run();
    }

    private String[] cmdLineMsgs;

    private Connection connection;

    private Session session;

    @Override
    public void run() {
        try {
            final ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MQ_URL);
            connection = factory.createConnection(MQ_USER, MQ_PASSWD);
            connection.start();

            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            // sendAllMessages( cmdLineMsgs.length == 0
            // ? new String[]{ "{ \"stage\":17 }" }
            // : cmdLineMsgs );

            // String[] messagesToSend = {
            // "Hey there! How are you doing?",
            // "Just wanted to check in and see how your day is going!",
            // "Did you hear about the new update? It's pretty exciting!",
            // "Hope you're having a fantastic day!",
            // "Remember, you're awesome! Keep shining!"
            // };
            String[] messagesToSend = { "{ \"stage\":17 }" };
            sendAllMessages(messagesToSend);

        } catch (JMSException erk) {
            throw new RuntimeException(erk);
        } finally {
            closeResources();
        }
        System.out.println("Bye...");
    }

    private void sendAllMessages(String[] messages) throws JMSException {
        Destination destination = session.createQueue("stage");
        MessageProducer producer = session.createProducer(destination);
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
        for (String msg : messages) {
            TextMessage msgToSend = session.createTextMessage(msg);
            producer.send(msgToSend);
            System.out.println("Message that was sent:\n" + msg);
        }
    }

    private void closeResources() {
        try {
            if (session != null)
                session.close();
            if (connection != null)
                connection.close();
        } catch (JMSException ex) {
            // wut?
        }
        session = null;
        connection = null;
    }

}
