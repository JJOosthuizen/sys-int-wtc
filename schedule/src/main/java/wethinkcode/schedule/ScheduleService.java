package wethinkcode.schedule;

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import kong.unirest.json.JSONObject;

import javax.jms.*;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQTextMessage;

import wethinkcode.loadshed.common.mq.MQ;
import wethinkcode.loadshed.common.transfer.DayDO;
import wethinkcode.loadshed.common.transfer.ScheduleDO;
import wethinkcode.loadshed.common.transfer.SlotDO;

public class ScheduleService {
    public static final int DEFAULT_STAGE = 0;
    public static final int DEFAULT_PORT = 7002;
    public static final String MQ_TOPIC = "stage";
    private Connection connection;
    private int currentStage = DEFAULT_STAGE;

    private Javalin server;
    private int servicePort;

    public static void main(String[] args) {
        final ScheduleService svc = new ScheduleService().initialise();
        svc.start();

    }

    @VisibleForTesting
    ScheduleService initialise() {
        server = initHttpServer();
        setUpMessageListener();
        return this;
    }

    private void setUpMessageListener() {
        try {
            final ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MQ.URL);
            connection = factory.createConnection(MQ.USER, MQ.PASSWD);

            final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            final Destination dest = session.createTopic(MQ_TOPIC); // <-- NB: Topic, not Queue!

            final MessageConsumer receiver = session.createConsumer(dest);
            receiver.setMessageListener(new MessageListener() {
                @Override
                public void onMessage(Message m) {
                    if (m instanceof ActiveMQTextMessage) {
                        ActiveMQTextMessage textMessage = (ActiveMQTextMessage) m;
                        String text = null;
                        try {
                            text = textMessage.getText();
                        } catch (JMSException e) {
                            e.printStackTrace();
                        }

                        JSONObject jsonObject = new JSONObject(text);
                        currentStage = jsonObject.getInt("stage");

                        System.out.println("Received stage: " + currentStage);
                    }

                    System.out.println(m.toString());
                }
            });
            connection.start();

        } catch (JMSException erk) {
            throw new RuntimeException(erk);
        }
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
                .get("/{province}/{town}/{stage}", this::getSchedule)
                // .get("/{province}/{town}", this::getDefaultSchedule)
                .get("/{province}/{place}", this::getPlaceSchedule);
    }

    private Context getSchedule(Context ctx) {
        final String province = ctx.pathParam("province");
        final String townName = ctx.pathParam("town");
        final String stageStr = ctx.pathParam("stage");

        if (province.isEmpty() || townName.isEmpty() || stageStr.isEmpty()) {
            ctx.status(HttpStatus.BAD_REQUEST);
            return ctx;
        }
        final int stage = Integer.parseInt(stageStr);
        if (stage < 0 || stage > 8) {
            ctx.status(HttpStatus.BAD_REQUEST);
            return ctx;
        }

        final Optional<ScheduleDO> schedule = getSchedule(province, townName, stage);

        ctx.status(schedule.isPresent()
                ? HttpStatus.OK
                : HttpStatus.NOT_FOUND);
        return ctx.json(schedule.orElseGet(ScheduleService::emptySchedule));
    }

    private Context getDefaultSchedule(Context ctx) {
        ctx.status(HttpStatus.OK).json(DEFAULT_STAGE);
        return ctx;
    }

    Optional<ScheduleDO> getSchedule(String province, String town, int stage) {
        return province.equalsIgnoreCase("Mars")
                ? Optional.empty()
                : Optional.of(mockSchedule());
    }

    private static ScheduleDO mockSchedule() {
        final List<SlotDO> slots = List.of(
                new SlotDO(LocalTime.of(2, 0), LocalTime.of(4, 0)),
                new SlotDO(LocalTime.of(10, 0), LocalTime.of(12, 0)),
                new SlotDO(LocalTime.of(18, 0), LocalTime.of(20, 0)));
        final List<DayDO> days = List.of(
                new DayDO(slots),
                new DayDO(slots),
                new DayDO(slots),
                new DayDO(slots));
        return new ScheduleDO(days);
    }

    private static ScheduleDO emptySchedule() {
        final List<SlotDO> slots = Collections.emptyList();
        final List<DayDO> days = Collections.emptyList();
        return new ScheduleDO(days);
    }

    private int extractStageFromMessage(Message message) throws JMSException {
        return message.getIntProperty("stage");
    }

    private void updateCurrentStage(int newStage) {
        System.out.println("Updated current load shedding stage: " + newStage);
    }

    private Context getPlaceSchedule(Context ctx) {
        final String province = ctx.pathParam("province");
        final String place = ctx.pathParam("place");

        Optional<ScheduleDO> schedule = getSchedule(province, place, currentStage);

        if (schedule.isPresent()) {
            return ctx.status(HttpStatus.OK).json(schedule.get());
        } else {
            return ctx.status(HttpStatus.NOT_FOUND).json("No schedule available for " + place + " in " + province);
        }
    }
}
