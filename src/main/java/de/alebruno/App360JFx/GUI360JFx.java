/**
 *     360JFx: multi-platform visualizer of 360 pictures
 *     Copyright (C) 2020  Alessandro Bruno
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */


package de.alebruno.App360JFx;

import com.Equi2Rect.Equi2Rect;

import javafx.application.Application;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.scene.*;
import javafx.scene.image.Image;
import javafx.scene.input.*;
import javafx.scene.paint.Color;
import javafx.scene.transform.Rotate;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.fxyz3d.scene.Skybox;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.Equi2Rect.EquirectangularToCubic;

import javax.imageio.ImageIO;

/**
 * @author Alessandro Bruno
 */
public class GUI360JFx extends Application {


    DoubleProperty anglex;
    DoubleProperty angley;
    DoubleProperty FOV;
    Double anchorX;
    Double anchorY;
    Double anchorAngleX;
    Double anchorAngleY;
    Skybox sky;
    Group atlas; // He holds the sky.
    Group root3D;
    Scene scene;
    BufferedImage[] skyboxImages;
    Image[] skyboxImagesFx;
    Stage stage;
    PerspectiveCamera camera;


    @Override
    public void start(Stage primaryStage) throws Exception {

        stage = primaryStage;
        atlas = new Group();
        camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(10000.0);
        FOV = new SimpleDoubleProperty(60.0);
        camera.fieldOfViewProperty().bind(FOV);
        camera.setVerticalFieldOfView(true);
        Rotate rx = new Rotate(180.0, Rotate.X_AXIS);
        camera.getTransforms().add(rx);

        BufferedImage image = ImageIO.read(getClass().getResourceAsStream("/Schwarzenberg.jpg"));
        openPanoramaImage(image);
        root3D = new Group(camera, new AmbientLight(Color.WHITE), atlas);
        scene = new Scene(root3D, 800, 600, true, SceneAntialiasing.BALANCED);
        scene.setCamera(camera);
        primaryStage.setTitle("360JFx");
        primaryStage.setScene(scene);

        Rotate rotx = new Rotate(0.0, Rotate.X_AXIS);
        Rotate roty = new Rotate(0.0, Rotate.Y_AXIS);
        anglex = new SimpleDoubleProperty(0.0);
        angley = new SimpleDoubleProperty(0.0);
        rotx.angleProperty().bind(anglex);
        roty.angleProperty().bind(angley);
        atlas.getTransforms().addAll(rotx, roty);

        scene.setOnMousePressed(event -> {
            anchorX = event.getSceneX();
            anchorY = event.getSceneY();
            anchorAngleX = anglex.get();
            anchorAngleY = angley.get();
        });

        scene.setOnMouseDragged(event -> {
            anglex.set(anchorAngleX + (anchorY - event.getSceneY()) * FOV.getValue()/ 600.0);
            angley.set(anchorAngleY + (anchorX - event.getSceneX()) * FOV.getValue() / 600.0);
        });

        scene.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent){
                if(mouseEvent.getButton().equals(MouseButton.PRIMARY)) {
                    if(mouseEvent.getClickCount() == 2){
                        primaryStage.setFullScreen(true);
                    }
                }

                if(mouseEvent.getButton().equals(MouseButton.SECONDARY)) {
                    FileChooser fileChooser = new FileChooser();
                    configureFileChooser(fileChooser);
                    File file = fileChooser.showOpenDialog(stage);
                    openPanoramaFile(file);
                }
            }
        });

        scene.setOnDragOver(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
                if (event.getDragboard().hasFiles()) {
                    event.acceptTransferModes(TransferMode.ANY);
                }
                event.consume();
            }
        });

        scene.setOnScroll(new EventHandler<ScrollEvent>() {
            @Override
            public void handle(ScrollEvent event) {
                 FOV.setValue(returnInsideRange(FOV.getValue() - (event.getDeltaX() + event.getDeltaY())/20.0,
                         20.0, 100.0));
            }
        });

        scene.setOnDragDropped(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
                openPanoramaFile(event.getDragboard().getFiles().get(0));
                event.consume();
            }
        });

        primaryStage.show();
    }

    Double returnInsideRange(Double value, Double min, Double max) {
        return Math.min(Math.max(value, min), max);
    }

    public void openPanoramaFile(File file) {
        try {
            System.out.printf("Processing image file: %s\n", file);
            BufferedImage image = EquirectangularToCubic.loadImage(file);
            openPanoramaImage(image);
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    public void openPanoramaImage(BufferedImage image)
    {
        skyboxImages = EquirectangularToCubic.processImage(image);
        skyboxImagesFx = new Image[6];

        for (int i = 0; i < 6; i++)
        {
            skyboxImagesFx[i] = SwingFXUtils.toFXImage(skyboxImages[i], null);
        }

        sky = new Skybox(skyboxImagesFx[4],
                skyboxImagesFx[5],
                skyboxImagesFx[3],
                skyboxImagesFx[1],
                skyboxImagesFx[0],
                skyboxImagesFx[2],
                1000, camera);
        while (atlas.getChildren().size() > 0) atlas.getChildren().remove(0);
        atlas.getChildren().add(sky);
    }

    private static void configureFileChooser(
            final FileChooser fileChooser) {
        fileChooser.setTitle("Select Panorama");
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home"))
        );
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", "*.*"),
                new FileChooser.ExtensionFilter("JPG", new ArrayList<String>(Arrays.asList("*.jpg", "*.jpeg","*.JPG","*.JPEG","*.Jpg","*.Jpeg"))),
                new FileChooser.ExtensionFilter("PNG", new ArrayList<String>(Arrays.asList("*.png", "*.PNG")))
        );
    }

    public static void main(String[] args) {
        launch(args);
    }
}
