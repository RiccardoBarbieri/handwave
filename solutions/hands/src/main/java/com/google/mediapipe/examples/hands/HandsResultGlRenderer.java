// Copyright 2021 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.mediapipe.examples.hands;

import android.opengl.GLES20;
import android.util.Log;

import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.formats.proto.LandmarkProto.Landmark;
import com.google.mediapipe.formats.proto.LandmarkProto.LandmarkList;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.solutioncore.ResultGlRenderer;
import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsResult;
import com.hands.utils.Constants;
import com.hands.utils.HandPoints;
import com.hands.utils.Utils;
import com.hands.utils.VectorUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;


/**
 * A custom implementation of {@link ResultGlRenderer} to render {@link HandsResult}.
 */
public class HandsResultGlRenderer implements ResultGlRenderer<HandsResult> {
    private static final String TAG = "HandsResultGlRenderer";

    private static final float[] LEFT_HAND_CONNECTION_COLOR = new float[]{0.2f, 1f, 0.2f, 1f};
    private static final float[] RIGHT_HAND_CONNECTION_COLOR = new float[]{1f, 0.2f, 0.2f, 1f};
    private static final float CONNECTION_THICKNESS = 25.0f;
    private static final float[] LEFT_HAND_HOLLOW_CIRCLE_COLOR = new float[]{0.2f, 1f, 0.2f, 1f};
    private static final float[] RIGHT_HAND_HOLLOW_CIRCLE_COLOR = new float[]{1f, 0.2f, 0.2f, 1f};
    private static final float HOLLOW_CIRCLE_RADIUS = 0.01f;
    private static final float[] LEFT_HAND_LANDMARK_COLOR = new float[]{1f, 0.2f, 0.2f, 1f};
    private static final float[] RIGHT_HAND_LANDMARK_COLOR = new float[]{0.2f, 1f, 0.2f, 1f};
    private static final float[] DRAWING_COLOR = new float[]{1f, 1f, 1f, 1f};
    private static final float LANDMARK_RADIUS = 0.008f;
    private static final int NUM_SEGMENTS = 120;
    private static final String VERTEX_SHADER =
            "uniform mat4 uProjectionMatrix;\n"
                    + "attribute vec4 vPosition;\n"
                    + "void main() {\n"
                    + "  gl_Position = uProjectionMatrix * vPosition;\n"
                    + "}";
    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n"
                    + "uniform vec4 uColor;\n"
                    + "void main() {\n"
                    + "  gl_FragColor = uColor;\n"
                    + "}";
    private int program;
    private int positionHandle;
    private int projectionMatrixHandle;
    private int colorHandle;

    public String log = "";

    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }

    @Override
    public void setupRendering() {
        program = GLES20.glCreateProgram();
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition");
        projectionMatrixHandle = GLES20.glGetUniformLocation(program, "uProjectionMatrix");
        colorHandle = GLES20.glGetUniformLocation(program, "uColor");
    }

    @Override
    public void renderResult(HandsResult result, float[] projectionMatrix) {
        if (result == null) {
            return;
        }
        GLES20.glUseProgram(program);
        GLES20.glUniformMatrix4fv(projectionMatrixHandle, 1, false, projectionMatrix, 0);
        GLES20.glLineWidth(CONNECTION_THICKNESS);

        int numHands = result.multiHandLandmarks().size();
        List<LandmarkList> landmarks = result.multiHandWorldLandmarks();
        LandmarkProto.NormalizedLandmarkList normalizedLandmarks;
        List<HandPoints> relPoints = new ArrayList<HandPoints>();
        relPoints.add(HandPoints.INDEX_LOWER);
        relPoints.add(HandPoints.INDEX_UPPER);

        for (int i = 0; i < numHands; ++i) {
            normalizedLandmarks = result.multiHandLandmarks().get(0);
            boolean isLeftHand = result.multiHandedness().get(i).getLabel().equals("Left");
            drawVectorUtils(normalizedLandmarks.getLandmark(HandPoints.INDEX_TIP.getValue()),
                    normalizedLandmarks.getLandmark(HandPoints.INDEX_BASE.getValue()),
                    isLeftHand ? LEFT_HAND_CONNECTION_COLOR : RIGHT_HAND_CONNECTION_COLOR);

            //for (int j = HandPoints.INDEX_BASE.getValue(); j <= HandPoints.INDEX_TIP.getValue(); j++) {
            //    Log.d("DRAWING", "Drawing landmark " + j);
            //    drawVectorUtils(normalizedLandmarks.getLandmark(i),
            //            normalizedLandmarks.getLandmark(i + 1),
            //            isLeftHand ? LEFT_HAND_CONNECTION_COLOR : RIGHT_HAND_CONNECTION_COLOR);
            //}
            drawVectorUtils(normalizedLandmarks.getLandmark(HandPoints.INDEX_BASE.getValue()),
                    normalizedLandmarks.getLandmark(HandPoints.INDEX_LOWER.getValue()),
                    isLeftHand ? LEFT_HAND_CONNECTION_COLOR : RIGHT_HAND_CONNECTION_COLOR);

            drawConnections(
                    result.multiHandLandmarks().get(i).getLandmarkList(),
                    isLeftHand ? LEFT_HAND_CONNECTION_COLOR : RIGHT_HAND_CONNECTION_COLOR);
            for (NormalizedLandmark landmark : result.multiHandLandmarks().get(i).getLandmarkList()) {
                int index = result.multiHandLandmarks().get(i).getLandmarkList().indexOf(landmark);
                if (index != 5 && index != 6 && index !=  7 && index !=  8) {
                    continue;
                }
                // Draws the landmark.
                drawCircle(
                        landmark.getX(),
                        landmark.getY(),
                        isLeftHand ? LEFT_HAND_LANDMARK_COLOR : RIGHT_HAND_LANDMARK_COLOR);
                // Draws a hollow circle around the landmark.
                drawHollowCircle(
                        landmark.getX(),
                        landmark.getY(),
                        isLeftHand ? LEFT_HAND_HOLLOW_CIRCLE_COLOR : RIGHT_HAND_HOLLOW_CIRCLE_COLOR);
            }

            log = "";
            //int numeroLivelli = 70;
            //LandmarkList landmarksFirstHand = landmarks.get(0);
            //LandmarkProto.NormalizedLandmarkList landmarksFirstHandNormal = result.multiHandLandmarks().get(0);
            //log = "\n" + HandPoints.THUMB_TIP + ": " + Utils.fingerLevelsToWrist(numeroLivelli, landmarksFirstHand).get(HandPoints.THUMB_TIP);
            //log += "\n" + HandPoints.INDEX_TIP + ": " + Utils.fingerLevelsToWrist(numeroLivelli, landmarksFirstHand).get(HandPoints.INDEX_TIP);
            //log += "\n" + HandPoints.MIDDLE_TIP + ": " + Utils.fingerLevelsToWrist(numeroLivelli, landmarksFirstHand).get(HandPoints.MIDDLE_TIP);
            //log += "\n" + HandPoints.RING_TIP + ": " + Utils.fingerLevelsToWrist(numeroLivelli, landmarksFirstHand).get(HandPoints.RING_TIP);
            //log += "\n" + HandPoints.PINKY_TIP + ": " + Utils.fingerLevelsToWrist(numeroLivelli, landmarksFirstHand).get(HandPoints.PINKY_TIP);
            ////add all fingers UPPER to log message
            //log += "\n\n" + HandPoints.THUMB_UPPER + ": " + Utils.fingerLevelsToWrist(numeroLivelli, landmarksFirstHand).get(HandPoints.THUMB_UPPER);
            //log += "\n" + HandPoints.INDEX_UPPER + ": " + Utils.fingerLevelsToWrist(numeroLivelli, landmarksFirstHand).get(HandPoints.INDEX_UPPER);
            //log += "\n" + HandPoints.MIDDLE_UPPER + ": " + Utils.fingerLevelsToWrist(numeroLivelli, landmarksFirstHand).get(HandPoints.MIDDLE_UPPER);
            //log += "\n" + HandPoints.RING_UPPER + ": " + Utils.fingerLevelsToWrist(numeroLivelli, landmarksFirstHand).get(HandPoints.RING_UPPER);
            //log += "\n" + HandPoints.PINKY_UPPER + ": " + Utils.fingerLevelsToWrist(numeroLivelli, landmarksFirstHand).get(HandPoints.PINKY_UPPER);

            //log += "\n\nWITH LANDMARKS";
            //log += "\n" + HandPoints.THUMB_LOWER + "_ERROR: " + VectorUtils.levelsToLine(Constants.NUMERO_LIVELLI,
            //        landmarksFirstHand.getLandmark(HandPoints.THUMB_BASE.getValue()),
            //        landmarksFirstHand.getLandmark(HandPoints.THUMB_LOWER.getValue()),
            //        landmarksFirstHand.getLandmark(HandPoints.THUMB_BASE.getValue()),
            //        landmarksFirstHand.getLandmark(HandPoints.THUMB_TIP.getValue()));
            //log += "\n" + HandPoints.THUMB_UPPER + "_ERROR: " + VectorUtils.levelsToLine(Constants.NUMERO_LIVELLI,
            //        landmarksFirstHand.getLandmark(HandPoints.THUMB_LOWER.getValue()),
            //        landmarksFirstHand.getLandmark(HandPoints.THUMB_UPPER.getValue()),
            //        landmarksFirstHand.getLandmark(HandPoints.THUMB_BASE.getValue()),
            //        landmarksFirstHand.getLandmark(HandPoints.THUMB_TIP.getValue()));
            //
            //log += "\n\nWITH NORMALIZED LANDMARKS";
            //log += "\n" + HandPoints.INDEX_LOWER + "_ERROR: " + VectorUtils.levelsToLine(Constants.NUMERO_LIVELLI,
            //        landmarksFirstHandNormal.getLandmark(HandPoints.INDEX_BASE.getValue()),
            //        landmarksFirstHandNormal.getLandmark(HandPoints.INDEX_LOWER.getValue()),
            //        landmarksFirstHandNormal.getLandmark(HandPoints.INDEX_BASE.getValue()),
            //        landmarksFirstHandNormal.getLandmark(HandPoints.INDEX_TIP.getValue()));
            //log += "\n" + HandPoints.INDEX_UPPER + "_ERROR: " + VectorUtils.levelsToLine(Constants.NUMERO_LIVELLI,
            //        landmarksFirstHandNormal.getLandmark(HandPoints.INDEX_LOWER.getValue()),
            //        landmarksFirstHandNormal.getLandmark(HandPoints.INDEX_UPPER.getValue()),
            //        landmarksFirstHandNormal.getLandmark(HandPoints.INDEX_BASE.getValue()),
            //        landmarksFirstHandNormal.getLandmark(HandPoints.INDEX_TIP.getValue()));

            //log += "\n\nINDEX_TIP -> INDEX_BASE" + Utils.getDistanceLevel(Constants.NUMERO_LIVELLI,
            //        landmarksFirstHand.getLandmark(HandPoints.INDEX_BASE.getValue()),
            //        landmarksFirstHand.getLandmark(HandPoints.INDEX_TIP.getValue()),
            //        landmarksFirstHand);

            //log += "\n\n" + HandPoints.THUMB_UPPER + ": "+ Utils.fingerLevelsToWrist(numeroLivelli,landmarks.get(0)).get(HandPoints.THUMB_UPPER);
            //log += "\n" + HandPoints.INDEX_UPPER + ": "+ Utils.fingerLevelsToWrist(numeroLivelli,landmarks.get(0)).get(HandPoints.INDEX_UPPER);
            //log += "\n" + HandPoints.MIDDLE_UPPER + ": "+ Utils.fingerLevelsToWrist(numeroLivelli,landmarks.get(0)).get(HandPoints.MIDDLE_UPPER);
            //log += "\n" + HandPoints.RING_UPPER + ": "+ Utils.fingerLevelsToWrist(numeroLivelli,landmarks.get(0)).get(HandPoints.RING_UPPER);
            //log += "\n" + HandPoints.PINKY_UPPER + ": "+ Utils.fingerLevelsToWrist(numeroLivelli,landmarks.get(0)).get(HandPoints.PINKY_UPPER);
            //
            //log += "\n\n";

            //DecimalFormat df = new DecimalFormat("#.##");
            //log = "\n" + HandPoints.THUMB_TIP + ":  "+ df.format(landmarkList.get(4).getX()) + "   " + df.format(landmarkList.get(4).getY());
            //log += "\n" + HandPoints.INDEX_TIP + ":     "+ df.format(landmarkList.get(8).getX()) + "   " + df.format(landmarkList.get(8).getY());
            //log += "\n" + HandPoints.MIDDLE_TIP + ": "+ df.format(landmarkList.get(12).getX()) + "   " + df.format(landmarkList.get(12).getY());
            //log += "\n" + HandPoints.RING_TIP + ":       "+ df.format(landmarkList.get(16).getX()) + "   " + df.format(landmarkList.get(16).getY());
            //log += "\n" + HandPoints.PINKY_TIP + ":     "+ df.format(landmarkList.get(20).getX()) + "   " + df.format(landmarkList.get(20).getY());
            //add all fingers UPPER to log message

            //log += "\n\n" + HandPoints.THUMB_UPPER + ": "+ Utils.fingerLevelsToWrist(numeroLivelli,landmarks.get(0)).get(HandPoints.THUMB_UPPER);
            //log += "\n" + HandPoints.INDEX_UPPER + ": "+ Utils.fingerLevelsToWrist(numeroLivelli,landmarks.get(0)).get(HandPoints.INDEX_UPPER);
            //log += "\n" + HandPoints.MIDDLE_UPPER + ": "+ Utils.fingerLevelsToWrist(numeroLivelli,landmarks.get(0)).get(HandPoints.MIDDLE_UPPER);
            //log += "\n" + HandPoints.RING_UPPER + ": "+ Utils.fingerLevelsToWrist(numeroLivelli,landmarks.get(0)).get(HandPoints.RING_UPPER);
            //log += "\n" + HandPoints.PINKY_UPPER + ": "+ Utils.fingerLevelsToWrist(numeroLivelli,landmarks.get(0)).get(HandPoints.PINKY_UPPER);

            //log += "\n\n";




        }

    }

    private void drawVectorUtils(NormalizedLandmark landmark1, NormalizedLandmark landmark2, float[] colorArray) {
        GLES20.glUniform4fv(colorHandle, 1, colorArray, 0);
        float[] vertex = {landmark1.getX(), landmark1.getY(), landmark2.getX(), landmark2.getY()};
        FloatBuffer vertexBuffer =
                ByteBuffer.allocateDirect(vertex.length * 4)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer()
                        .put(vertex);
        vertexBuffer.position(0);
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2);
    }

    //_____________________________________________________
    // nuovo codice test scrive su feed camera
    private void drawDistanceLine(List<Landmark> handLandmarkList, float[] colorArray) {
        GLES20.glUniform4fv(colorHandle, 1, colorArray, 0);
        Landmark wrist = handLandmarkList.get(0);
        float x = 0.25f;
        float y = 0.05f;
        for (int i = 8; i < 21; i += 4) {
            Landmark finger = handLandmarkList.get(i);
            float distance = (float) Math.sqrt(Math.pow(finger.getX() - wrist.getX(), 2) + Math.pow(finger.getY() - wrist.getY(), 2) + Math.pow(finger.getZ() - wrist.getZ(), 2));
            float[] vertex = {x + (0.05f * ((i - 8) / 4)), y, x + (0.05f * ((i - 8) / 4)), y + distance * 2};
            FloatBuffer vertexBuffer =
                    ByteBuffer.allocateDirect(vertex.length * 4)
                            .order(ByteOrder.nativeOrder())
                            .asFloatBuffer()
                            .put(vertex);
            vertexBuffer.position(0);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2);
        }
    }
    //_____________________________________________________

    /**
     * Deletes the shader program.
     *
     * <p>This is only necessary if one wants to release the program while keeping the context around.
     */
    public void release() {
        GLES20.glDeleteProgram(program);
    }

    private void drawConnections(List<NormalizedLandmark> handLandmarkList, float[] colorArray) {
        GLES20.glUniform4fv(colorHandle, 1, colorArray, 0);
        for (Hands.Connection c : Hands.HAND_CONNECTIONS) {
            if (c.start() != 5 && c.start() != 6 && c.start() != 7 || c.end() == 9) {
                continue;
            }
            NormalizedLandmark start = handLandmarkList.get(c.start());
            NormalizedLandmark end = handLandmarkList.get(c.end());
            float[] vertex = {start.getX(), start.getY(), end.getX(), end.getY()};
            FloatBuffer vertexBuffer =
                    ByteBuffer.allocateDirect(vertex.length * 4)
                            .order(ByteOrder.nativeOrder())
                            .asFloatBuffer()
                            .put(vertex);
            vertexBuffer.position(0);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2);
        }
    }

    private void drawCircle(float x, float y, float[] colorArray) {
        GLES20.glUniform4fv(colorHandle, 1, colorArray, 0);
        int vertexCount = NUM_SEGMENTS + 2;
        float[] vertices = new float[vertexCount * 3];
        vertices[0] = x;
        vertices[1] = y;
        vertices[2] = 0;
        for (int i = 1; i < vertexCount; i++) {
            float angle = 2.0f * i * (float) Math.PI / NUM_SEGMENTS;
            int currentIndex = 3 * i;
            vertices[currentIndex] = x + (float) (LANDMARK_RADIUS * Math.cos(angle));
            vertices[currentIndex + 1] = y + (float) (LANDMARK_RADIUS * Math.sin(angle));
            vertices[currentIndex + 2] = 0;
        }
        FloatBuffer vertexBuffer =
                ByteBuffer.allocateDirect(vertices.length * 4)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer()
                        .put(vertices);
        vertexBuffer.position(0);
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, vertexCount);
    }

    private void drawHollowCircle(float x, float y, float[] colorArray) {
        GLES20.glUniform4fv(colorHandle, 1, colorArray, 0);
        int vertexCount = NUM_SEGMENTS + 1;
        float[] vertices = new float[vertexCount * 3];
        for (int i = 0; i < vertexCount; i++) {
            float angle = 2.0f * i * (float) Math.PI / NUM_SEGMENTS;
            int currentIndex = 3 * i;
            vertices[currentIndex] = x + (float) (HOLLOW_CIRCLE_RADIUS * Math.cos(angle));
            vertices[currentIndex + 1] = y + (float) (HOLLOW_CIRCLE_RADIUS * Math.sin(angle));
            vertices[currentIndex + 2] = 0;
        }
        FloatBuffer vertexBuffer =
                ByteBuffer.allocateDirect(vertices.length * 4)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer()
                        .put(vertices);
        vertexBuffer.position(0);
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, vertexCount);
    }
}