package graphics;

import field.Direction;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Nicolas on 17/10/2016
 * Rewritten to use Java2D/Swing for macOS Apple Silicon compatibility.
 *
 * This class handles the window and all drawing operations.
 * It replaces the original LWJGL/OpenGL implementation which does not
 * support the fixed-function pipeline on macOS Sequoia + Apple Silicon.
 */

public class Window extends JPanel {

    public static String DEFAULT_WINDOW_NAME = "Parcel Wars";
    public static int WIDTH = 1080;
    public static int HEIGHT = 720;
    public static boolean FULL_SCREEN = false;
    public static boolean ENABLE_GRID = true;
    public static int LINE_WIDTH = 3;
    public static boolean USE_BITMAPS = false;
    public static int DELAY_MS = 90;
    public static boolean MUTE = false;

    private final Drawer drawer;
    private final JFrame frame;
    private volatile boolean running = true;
    private boolean previousIsSearching = false, isSearching = false;

    // Current drawing context — valid only during paintComponent
    private static Graphics2D g2d;
    private static java.awt.Color currentColor = java.awt.Color.WHITE;

    // Texture atlas for USE_BITMAPS mode
    private BufferedImage textureImage;
    private int textureWidth, textureHeight;

    public Window(Drawer drawer) {
        this.drawer = drawer;
        setBackground(java.awt.Color.WHITE);
        setFocusable(true);

        frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.getContentPane().add(this);

        loadTexture();
        setDimensions();

        // Key handling — registered on both frame and panel for macOS compatibility
        KeyAdapter keyHandler = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_UP:    drawer.moveSelectedRobotManually(Direction.UP);    break;
                    case KeyEvent.VK_DOWN:  drawer.moveSelectedRobotManually(Direction.DOWN);  break;
                    case KeyEvent.VK_LEFT:  drawer.moveSelectedRobotManually(Direction.LEFT);  break;
                    case KeyEvent.VK_RIGHT: drawer.moveSelectedRobotManually(Direction.RIGHT); break;
                }
            }
            @Override
            public void keyReleased(KeyEvent e) {
                int key = e.getKeyCode();
                if (key == KeyEvent.VK_ESCAPE) {
                    running = false;
                    frame.dispose();
                } else if (key == KeyEvent.VK_D) {
                    frame.setTitle(DEFAULT_WINDOW_NAME + " - " + drawer.getLevelName() + " - A* Searching: Dijkstra heuristic");
                    drawer.launchAStarWithDijkstra();
                    frame.setTitle(DEFAULT_WINDOW_NAME + " - " + drawer.getLevelName());
                } else if (key == KeyEvent.VK_SEMICOLON || key == KeyEvent.VK_M) {
                    frame.setTitle(DEFAULT_WINDOW_NAME + " - " + drawer.getLevelName() + " - A* Searching: Manhattan heuristic");
                    drawer.launchAStarWithManhattan();
                    frame.setTitle(DEFAULT_WINDOW_NAME + " - " + drawer.getLevelName());
                } else if (key == KeyEvent.VK_E) {
                    frame.setTitle(DEFAULT_WINDOW_NAME + " - " + drawer.getLevelName() + " - A* Searching: Euclide heuristic");
                    drawer.launchAStarWithEuclide();
                    frame.setTitle(DEFAULT_WINDOW_NAME + " - " + drawer.getLevelName());
                } else if (key == KeyEvent.VK_S) {
                    if (MUTE) { MUTE = false; drawer.unMuteSounds(); }
                    else      { MUTE = true;  drawer.muteSounds();   }
                } else if (key == KeyEvent.VK_G) {
                    USE_BITMAPS = !USE_BITMAPS;
                } else if (key >= KeyEvent.VK_0 && key <= KeyEvent.VK_9) {
                    isSearching = previousIsSearching = false;
                    drawer.setSearchField("level" + (key - KeyEvent.VK_0));
                    frame.setTitle(DEFAULT_WINDOW_NAME + " - " + drawer.getLevelName());
                    setDimensions();
                }
            }
        };
        frame.addKeyListener(keyHandler);
        this.addKeyListener(keyHandler);

        // Mouse handling
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (SwingUtilities.isLeftMouseButton(e)) {
                    double xPos = (double) WIDTH  * e.getX() / getWidth();
                    double yPos = (double) HEIGHT * e.getY() / getHeight();
                    drawer.selectRobotAtScreenPos(xPos, yPos);
                }
            }
        });

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                running = false;
            }
        });

        frame.setVisible(true);
        SwingUtilities.invokeLater(() -> {
            frame.toFront();
            frame.requestFocus();
            this.requestFocusInWindow();
        });
    }

    public void run() {
        javax.swing.Timer timer = new javax.swing.Timer(DELAY_MS, e -> {
            isSearching = drawer.updateMatrix();
            if (!isSearching && previousIsSearching) {
                drawer.playSound(drawer.goalReachedSoundIndex);
            }
            previousIsSearching = isSearching;
            repaint();
        });
        timer.start();

        while (running) {
            try { Thread.sleep(50); } catch (InterruptedException e) { break; }
        }
        timer.stop();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        // Scale so all drawing code can use WIDTH/HEIGHT coordinates regardless of actual window size
        g2d.scale((double) getWidth() / WIDTH, (double) getHeight() / HEIGHT);
        g2d.setColor(java.awt.Color.WHITE);
        g2d.fillRect(0, 0, WIDTH, HEIGHT);
        drawer.drawMatrix();
        g2d.dispose();
        g2d = null;
    }

    private void loadTexture() {
        try (InputStream input = getClass().getResourceAsStream("/resources/textures/texture.png")) {
            if (input != null) {
                textureImage = ImageIO.read(input);
                if (textureImage != null) {
                    textureWidth  = textureImage.getWidth();
                    textureHeight = textureImage.getHeight();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void setColor(Color color) {
        currentColor = new java.awt.Color(color.getRedValue(), color.getGreenValue(), color.getBlueValue());
        if (g2d != null) g2d.setColor(currentColor);
    }

    public void drawTexturedQuad(float startPosX, float startPosY, float width, float height, ImageData imageData) {
        if (g2d == null || textureImage == null) return;
        int texX = (int)(imageData.getTextureRangeX1() * textureWidth);
        int texY = (int)(imageData.getTextureRangeY1() * textureHeight);
        int texW = (int)((imageData.getTextureRangeX2() - imageData.getTextureRangeX1()) * textureWidth);
        int texH = (int)((imageData.getTextureRangeY2() - imageData.getTextureRangeY1()) * textureHeight);
        texX = Math.max(0, Math.min(texX, textureWidth  - 1));
        texY = Math.max(0, Math.min(texY, textureHeight - 1));
        texW = Math.max(1, Math.min(texW, textureWidth  - texX));
        texH = Math.max(1, Math.min(texH, textureHeight - texY));
        g2d.drawImage(textureImage.getSubimage(texX, texY, texW, texH),
                (int) startPosX, (int) startPosY, (int) width, (int) height, null);
    }

    public void drawRectangle(float xTopLeft, float yTopLeft, float xBottomRight, float yBottomRight) {
        if (g2d == null) return;
        g2d.setColor(currentColor);
        g2d.fillRect((int) xTopLeft, (int) yTopLeft,
                (int)(xBottomRight - xTopLeft), (int)(yBottomRight - yTopLeft));
    }

    public void drawLine(float lineWidth, float x1, float y1, float x2, float y2) {
        if (g2d == null) return;
        g2d.setColor(currentColor);
        g2d.setStroke(new BasicStroke(lineWidth));
        g2d.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
    }

    private void setDimensions() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int cols = drawer.getLevelColumnNumber();
        int rows = drawer.getLevelRowNumber();
        int dimW = (int) Math.ceil(0.95f * screen.width  / cols);
        int dimH = (int) Math.ceil(0.90f * screen.height / rows);
        if (dimW < dimH) { WIDTH = dimW * cols; HEIGHT = dimW * rows; }
        else             { WIDTH = dimH * cols; HEIGHT = dimH * rows; }
        drawer.updateRatios();
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        frame.setTitle(DEFAULT_WINDOW_NAME + " - " + drawer.getLevelName());
        frame.pack();
        frame.setLocationRelativeTo(null);
    }
}
