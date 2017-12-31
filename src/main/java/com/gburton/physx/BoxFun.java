package com.gburton.physx;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.jbox2d.collision.shapes.*;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class BoxFun {

    private static final Logger logger = Logger.getLogger(BoxFun.class);

    private static final float TIME_STEP = 1 / 60f;
    private static final int POSITION_ITERATIONS = 30;
    private static final int VELOCITY_ITERATIONS = 10;
    private static final int ORIGINAL_HEIGHT = 500;
    private static final int WIDTH = 300;
    private static final float PIXEL_SCALE = ORIGINAL_HEIGHT / 15f;

    private final Random random = new Random();
    private final World world;
    private final List<Box> boxes = Collections.synchronizedList(new ArrayList<Box>());

    private Point originalMousePoint;
    private Point currentMousePoint;
    private JPanel backgroundPanel;

    class Box {
        final Fixture fixture;
        final Color color;

        Box(Fixture fixture, Color color) {
            this.fixture = fixture;
            this.color = color;
        }

        void drawBox(Graphics2D g2d) {
            PolygonShape shape = (PolygonShape) fixture.getShape();

            int vertexCount = shape.getVertexCount();
            int[] xPoints = new int[vertexCount];
            int[] yPoints = new int[vertexCount];

            Body body = fixture.getBody();
            Point bodyPosition = physicalToPixel(body.getPosition());

            for (int i = 0; i < vertexCount; i++) {
                Vec2 vertex = shape.getVertex(i);
                Point point = new Point(
                    (int) (vertex.x * PIXEL_SCALE),
                    (int) (vertex.y * -1 * PIXEL_SCALE)
                );
                xPoints[i] = point.x;
                yPoints[i] = point.y;
            }

            AffineTransform transform = g2d.getTransform();
            g2d.setColor(color);
            g2d.translate(bodyPosition.x, bodyPosition.y);
            g2d.rotate(body.getAngle() * -1);
            g2d.fillPolygon(xPoints, yPoints, vertexCount);
            g2d.setTransform(transform);
        }

    }

    public static void main(String[] args) throws InterruptedException {
        BasicConfigurator.configure();
        new BoxFun();
    }

    BoxFun() throws InterruptedException {
        world = new World(new Vec2(0f, -9.8f));

        BodyDef floorDef = new BodyDef();
        floorDef.type = BodyType.STATIC;
        floorDef.position = new Vec2(0.0f, 1f);

        EdgeShape floorShape = new EdgeShape();
        floorShape.set(new Vec2(-15, 0.f), new Vec2(15, 0.0f));

        FixtureDef floorFixtureDef = new FixtureDef();
        floorFixtureDef.shape = floorShape;

        Body floorBody = world.createBody(floorDef);
        floorBody.createFixture(floorFixtureDef);

        createPanel();

        Thread thread = new Thread() {
            public void run() {
                while (true) {
                    update();

                    backgroundPanel.repaint();

                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        interrupt();
                    }
                }
            }
        };
        thread.run();
    }

    private void createPanel() {
        JFrame frame = new JFrame();
        frame.setTitle("Box Fun");
        frame.setSize(new Dimension(WIDTH, ORIGINAL_HEIGHT));

        backgroundPanel = new JPanel() {
            @Override
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                render(g);
            }
        };

        backgroundPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                Vec2 originalPosition = pixelToPhysical(originalMousePoint);
                Vec2 newPosition = pixelToPhysical(e.getPoint());
                Vec2 size = newPosition.sub(originalPosition).abs();

                Vec2 middlePoint = newPosition
                    .sub(originalPosition)
                    .mul(0.5f)
                    .add(originalPosition);

                createFallingObject(
                    middlePoint,
                    size.x,
                    size.y);

                currentMousePoint = null;
                originalMousePoint = null;
            }

            @Override
            public void mousePressed(MouseEvent e) {
                originalMousePoint = e.getPoint();
            }
        });
        backgroundPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                currentMousePoint = e.getPoint();
            }
        });
        backgroundPanel.setBackground(Color.WHITE);
        frame.add(backgroundPanel);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }


    private synchronized void update() {
        world.step(TIME_STEP, VELOCITY_ITERATIONS, POSITION_ITERATIONS);
    }

    private synchronized void render(Graphics g) {
        if (currentMousePoint != null) {
            g.setColor(Color.GRAY);
            g.fillRect(
                Math.min(originalMousePoint.x, currentMousePoint.x),
                Math.min(originalMousePoint.y, currentMousePoint.y),
                Math.abs(currentMousePoint.x - originalMousePoint.x),
                Math.abs(currentMousePoint.y - originalMousePoint.y)
            );
        }

        for (Box box : boxes) {
            box.drawBox((Graphics2D) g);
        }
    }

    private synchronized void createFallingObject(Vec2 position, float width, float height) {
        width = Math.max(width / 2.0f, 0.1f);
        height = Math.max(height / 2.0f, 0.1f);

        logger.info("Creating new object: " + position + " " + width + " " + height);
        BodyDef fallingObject = new BodyDef();
        fallingObject.type = BodyType.DYNAMIC;
        fallingObject.position = position;
        fallingObject.linearVelocity = new Vec2(random.nextFloat(), 0);
        fallingObject.bullet = true;
        fallingObject.angularVelocity = random.nextFloat() * 3.141f;
        fallingObject.fixedRotation = false;

        PolygonShape box = new PolygonShape();
        box.setAsBox(width, height);

        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = box;
        fixtureDef.density = 10; // area * density = mass
        fixtureDef.friction = 1f;

        final Body fallingBody = world.createBody(fallingObject);

        Color color = new Color(random.nextInt(255), random.nextInt(255), random.nextInt(255));
        boxes.add(new Box(fallingBody.createFixture(fixtureDef), color));
    }

    private Point physicalToPixel(Vec2 vec2) {
        Point point = new Point(
            (int) (vec2.x * PIXEL_SCALE),
            (int) (vec2.y * -1 * PIXEL_SCALE + backgroundPanel.getHeight())
        );
        if (logger.isTraceEnabled()) {
            logger.trace("Converting physicalToPixel: " + vec2 + " " + point);
        }
        return point;
    }

    private Vec2 pixelToPhysical(Point point) {
        float x = (float) point.getX() / PIXEL_SCALE;
        float y = (float) (backgroundPanel.getHeight() - point.getY()) / PIXEL_SCALE;
        if (logger.isDebugEnabled()) {
            logger.debug("Converting pixelToPhysical: " + point + " (" + x + ", " + y + ")");
        }
        return new Vec2(x, y);
    }
}
