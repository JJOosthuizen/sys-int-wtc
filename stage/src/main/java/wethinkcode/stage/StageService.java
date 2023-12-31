package wethinkcode.stage;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.ActiveMQConnectionFactory;

import com.google.common.annotations.VisibleForTesting;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import wethinkcode.loadshed.common.transfer.StageDO;

/**
 * I provide a REST API that reports the current loadshedding "stage". I provide
 * two endpoints:
 * <dl>
 * <dt>GET /stage
 * <dd>report the current stage of loadshedding as a JSON serialisation of a
 * {@code StageDO} data/transfer object
 * <dt>POST /stage
 * <dd>set a new loadshedding stage/level by POSTing a JSON-serialised
 * {@code StageDO} instance as the body of the
 * request.
 * </ul>
 */
public class StageService {
    public static final int DEFAULT_STAGE = 0; // no loadshedding. Ha!

    public static final int DEFAULT_PORT = 7001;

    public static final String MQ_TOPIC_NAME = "stage";

    public static void main(String[] args) {
        final StageService svc = new StageService().initialise();
        svc.start();
    }

    private int loadSheddingStage;

    private Javalin server;

    private int servicePort;

    @VisibleForTesting
    StageService initialise() {
        return initialise(DEFAULT_STAGE);
    }

    @VisibleForTesting
    StageService initialise(int initialStage) {
        loadSheddingStage = initialStage;
        assert loadSheddingStage >= 0;

        server = initHttpServer();
        return this;
    }

    public void start() {
        start(DEFAULT_PORT);
    }

    @VisibleForTesting
    void start(int networkPort) {
        servicePort = networkPort;
        run();
    }

    public void stop() {
        server.stop();
    }

    public void run() {
        server.start(servicePort);
    }

    private Javalin initHttpServer() {
        return Javalin.create()
                .get("/stage", this::getCurrentStage)
                .post("/stage", this::setNewStage);
    }

    private Context getCurrentStage(Context ctx) {
        return ctx.json(new StageDO(loadSheddingStage));
    }

    private Context setNewStage(Context ctx) {
        final StageDO stageData = ctx.bodyAsClass(StageDO.class);
        final int newStage = stageData.getStage();
        if (newStage >= 0) {
            loadSheddingStage = newStage;
            broadcastStageChangeEvent(ctx);
            ctx.status(HttpStatus.OK);
        } else {
            ctx.status(HttpStatus.BAD_REQUEST);
        }
        return ctx.json(new StageDO(loadSheddingStage));
    }

    private void broadcastStageChangeEvent(Context ctx) {
        try {
            // Create a connection factory
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("tcp://localhost:61616");

            // Create a connection
            Connection connection = factory.createConnection();
            connection.start();

            // Create a session
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Create the topic
            Topic topic = session.createTopic(MQ_TOPIC_NAME);

            // Create a producer for the topic
            MessageProducer producer = session.createProducer(topic);

            // Create a message
            String messageText = "{ \"stage\": " + loadSheddingStage + " }";
            TextMessage message = session.createTextMessage(messageText);

            // Send the message
            producer.send(message);

            // Clean up resources
            producer.close();
            session.close();
            connection.close();
        } catch (JMSException e) {
            e.printStackTrace();
            // Handle exception
        }
    }

}
