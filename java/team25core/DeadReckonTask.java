package team25core;

/*
 * FTC Team 25: cmacfarl, September 01, 2015
 */

import com.qualcomm.robotcore.util.RobotLog;

public class DeadReckonTask extends RobotTask {

    public enum EventKind {
        SEGMENT_DONE,
        SENSOR_SATISFIED,
        BOTH_SENSORS_SATISFIED,
        RIGHT_SENSOR_SATISFIED,
        LEFT_SENSOR_SATISFIED,
        PATH_DONE,
    }

    protected enum DoneReason {
        ENCODER_REACHED,
        SENSOR_SATISFIED,
        BOTH_SENSORS_SATISFIED,
        RIGHT_SENSOR_SATISFIED,
        LEFT_SENSOR_SATISFIED,
    };

    public class DeadReckonEvent extends RobotEvent {

        public EventKind kind;
        public int segment_num;

        public DeadReckonEvent(RobotTask task, EventKind k, int segment_num)
        {
            super(task);
            kind = k;
            this.segment_num = segment_num;
        }

        @Override
        public String toString()
        {
            return (super.toString() + "DeadReckon Event " + kind + " " + segment_num);
        }
    }

    protected class LimitSwitchListener implements RobotEventListener {
        @Override
        public void handleEvent(RobotEvent event)
        {
            DeadReckon.Segment segment;

            segment = dr.getCurrentSegment();
            if (segment != null) {
                segment.state = DeadReckon.SegmentState.DONE;
            }
        }
    }

    protected enum SensorsInstalled {
        SENSORS_NONE,
        SENSORS_ONE,
        SENSORS_TWO,
    };

    protected SensorsInstalled sensorsInstalled;
    protected DeadReckon dr;
    protected int num;
    protected boolean waiting;
    protected SensorCriteria leftCriteria;
    protected SensorCriteria rightCriteria;
    protected DoneReason reason;

    SingleShotTimerTask sst;
    int waitState = 0;

    public DeadReckonTask(Robot robot, DeadReckon dr)
    {
        super(robot);

        this.sensorsInstalled = SensorsInstalled.SENSORS_NONE;
        this.num = 0;
        this.dr = dr;
        this.waiting = false;
        this.waitState = 0;
        this.leftCriteria = null;
        this.rightCriteria = null;
    }

    public DeadReckonTask(Robot robot, DeadReckon dr, SensorCriteria criteria)
    {
        super(robot);

        this.sensorsInstalled = SensorsInstalled.SENSORS_ONE;
        this.num = 0;
        this.dr = dr;
        this.waiting = false;
        this.waitState = 0;
        this.leftCriteria = criteria;
        this.rightCriteria = null;
    }

    public DeadReckonTask(Robot robot, DeadReckon dr, SensorCriteria leftCriteria, SensorCriteria rightCriteria)
    {
        super(robot);

        this.sensorsInstalled = SensorsInstalled.SENSORS_TWO;
        this.num = 0;
        this.dr = dr;
        this.waiting = false;
        this.waitState = 0;
        this.leftCriteria = leftCriteria;
        this.rightCriteria = rightCriteria;
    }

    @Override
    public void start()
    {
        // TODO: ??
    }

    @Override
    public void stop()
    {
        robot.removeTask(this);
    }

    @Override
    public boolean timeslice()
    {
        DeadReckon.Segment segment;

        /*
         * Get current segment
         */
        segment = dr.getCurrentSegment();

        if (segment == null) {
            if (reason == DoneReason.ENCODER_REACHED) {
                dr.logEncoderPosition();
                RobotLog.e("251 Dead reckon path done");
                robot.queueEvent(new DeadReckonEvent(this, EventKind.PATH_DONE, num));
            } else if (reason == DoneReason.SENSOR_SATISFIED) {
                RobotLog.e("251 Dead reckon sensor criteria satisfied");
                robot.queueEvent(new DeadReckonEvent(this, EventKind.SENSOR_SATISFIED, num));
            } else if (reason == DoneReason.BOTH_SENSORS_SATISFIED) {
                RobotLog.e("251 Dead reckon both sensor criteria satisfied");
                robot.queueEvent(new DeadReckonEvent(this, EventKind.BOTH_SENSORS_SATISFIED, num));
            } else if (reason == DoneReason.LEFT_SENSOR_SATISFIED) {
                RobotLog.e("251 Dead reckon left sensor criteria segment %d satisfied", num);
                robot.queueEvent(new DeadReckonEvent(this, EventKind.LEFT_SENSOR_SATISFIED, num));
            } else if (reason == DoneReason.RIGHT_SENSOR_SATISFIED) {
                RobotLog.e("251 Dead reckon right sensor criteria segment %d satisfied", num);
                robot.queueEvent(new DeadReckonEvent(this, EventKind.RIGHT_SENSOR_SATISFIED, num));
            } else {
                RobotLog.e("Oops, unknown reason for dead reckon stop");
                robot.queueEvent(new DeadReckonEvent(this, EventKind.PATH_DONE, num));
            }
            /*
             * Make sure it's stopped.
             */
            RobotLog.i("251 Done with path, stopping all");
            dr.stop();
            return true;
        } else if (segment.state == DeadReckon.SegmentState.DONE) {
            if (reason == DoneReason.ENCODER_REACHED) {
                RobotLog.e("251 Dead reckon segment %d done", num);
                robot.queueEvent(new DeadReckonEvent(this, EventKind.SEGMENT_DONE, num));
            } else if (reason == DoneReason.SENSOR_SATISFIED) {
                RobotLog.e("251 Dead reckon sensor criteria segment %d satisfied", num);
                robot.queueEvent(new DeadReckonEvent(this, EventKind.SENSOR_SATISFIED, num));
            } else if (reason == DoneReason.BOTH_SENSORS_SATISFIED) {
                RobotLog.e("251 Dead reckon sensor criteria segment %d satisfied", num);
                robot.queueEvent(new DeadReckonEvent(this, EventKind.SENSOR_SATISFIED, num));
            } else if (reason == DoneReason.LEFT_SENSOR_SATISFIED) {
                RobotLog.e("251 Dead reckon left sensor criteria segment %d satisfied", num);
                robot.queueEvent(new DeadReckonEvent(this, EventKind.LEFT_SENSOR_SATISFIED, num));
            } else if (reason == DoneReason.RIGHT_SENSOR_SATISFIED) {
                RobotLog.e("251 Dead reckon right sensor criteria segment %d satisfied", num);
                robot.queueEvent(new DeadReckonEvent(this, EventKind.RIGHT_SENSOR_SATISFIED, num));
            }
        }

        switch (segment.state) {
        case INITIALIZE:
            dr.resetEncoders();
            segment.state = DeadReckon.SegmentState.ENCODER_RESET;
            break;
        case ENCODER_RESET:
            if (dr.areEncodersReset()) {
                segment.state = DeadReckon.SegmentState.SET_TARGET;
            } else {
                dr.resetEncoders();
            }
            break;
        case SET_TARGET:
            dr.encodersOn();
            dr.setTarget();
            segment.state = DeadReckon.SegmentState.CONSUME_SEGMENT;
            break;
        case CONSUME_SEGMENT:
            if (segment.type == DeadReckon.SegmentType.STRAIGHT) {
                dr.motorStraight(segment.speed);
            } else if (segment.type == DeadReckon.SegmentType.SIDEWAYS) {
                dr.motorSideways(segment.speed);
            } else if (segment.type == DeadReckon.SegmentType.BACK_LEFT_DIAGONAL) {
                dr.motorBackLeftDiagonal(segment.speed);
            } else if (segment.type == DeadReckon.SegmentType.BACK_RIGHT_DIAGONAL) {
                dr.motorBackRightDiagonal(segment.speed);
            } else {
                dr.motorTurn(segment.speed);
            }
            segment.state = DeadReckon.SegmentState.ENCODER_TARGET;
            break;
        case ENCODER_TARGET:
            if ((sensorsInstalled == SensorsInstalled.SENSORS_ONE) && (leftCriteria.satisfied())) {
                RobotLog.i("251 Solo sensor criteria satisfied");
                segment.state = DeadReckon.SegmentState.STOP_MOTORS;
                reason = DoneReason.SENSOR_SATISFIED;
            } else if (sensorsInstalled == SensorsInstalled.SENSORS_TWO) {
                if (leftCriteria.satisfied() && rightCriteria.satisfied()) {
                    RobotLog.i("251 Left and right criteria satisfied");
                    segment.state = DeadReckon.SegmentState.STOP_MOTORS;
                    reason = DoneReason.BOTH_SENSORS_SATISFIED;
                } else if (leftCriteria.satisfied()) {
                    RobotLog.i("251 Left criteria satisfied");
                    segment.state = DeadReckon.SegmentState.STOP_MOTORS;
                    reason = DoneReason.LEFT_SENSOR_SATISFIED;
                } else if (rightCriteria.satisfied()) {
                    RobotLog.i("251 Right criteria satisfied");
                    segment.state = DeadReckon.SegmentState.STOP_MOTORS;
                    reason = DoneReason.RIGHT_SENSOR_SATISFIED;
                }
            } else if (dr.hitTarget()) {
                segment.state = DeadReckon.SegmentState.STOP_MOTORS;
                reason = DoneReason.ENCODER_REACHED;
            }
            break;
        case STOP_MOTORS:
            dr.motorStraight(0.0);
            segment.state = DeadReckon.SegmentState.WAIT;
            waitState = 0;
        case WAIT:
            waitState++;
            /*
             * About 1/2 a second give or take, just insure we are stopped before moving on.
             */
            if (waitState > 50) {
                segment.state = DeadReckon.SegmentState.DONE;
            }
        case DONE:
            num++;
            dr.nextSegment();
            segment.state = DeadReckon.SegmentState.INITIALIZE;
        }

        robot.telemetry.addData("Segment: ", num);
        robot.telemetry.addData("State: ", segment.state.toString());

        return false;
    }
}
