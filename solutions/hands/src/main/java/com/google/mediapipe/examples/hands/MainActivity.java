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

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.exifinterface.media.ExifInterface;

import com.google.mediapipe.formats.proto.LandmarkProto.Landmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.solutioncore.CameraInput;
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView;
import com.google.mediapipe.solutioncore.VideoInput;
import com.google.mediapipe.solutions.hands.HandLandmark;
import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsOptions;
import com.google.mediapipe.solutions.hands.HandsResult;
import com.hands.gesture.CrabGesture;
import com.hands.gesture.FourGesture;
import com.hands.gesture.PinchGesture;
import com.hands.gesture.ScrollPageGesture;
import com.hands.gesture.ThreeGesture;
import com.hands.gesture.ThumbUpGesture;
import com.itextpdf.text.Document;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Main activity of MediaPipe Hands app.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private Hands hands;
    // Run the pipeline and the model inference on GPU or CPU.
    private static final boolean RUN_ON_GPU = true;
    Button btn;

    private File pdfFile, pdfFile1, pdfFile2;

    private enum InputSource {
        UNKNOWN,
        IMAGE,
        VIDEO,
        CAMERA,
    }

    private InputSource inputSource = InputSource.UNKNOWN;

    // Image demo UI and image loader components.
    private ActivityResultLauncher<Intent> imageGetter;
    private HandsResultImageView imageView;
    // Video demo UI and video loader components.
    private VideoInput videoInput;
    private ActivityResultLauncher<Intent> videoGetter;
    // Live camera demo UI and camera components.
    private CameraInput cameraInput;

    private SolutionGlSurfaceView<HandsResult> glSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupStaticImageDemoUiComponents();
        setupVideoDemoUiComponents();
        setupLiveDemoUiComponents();
        //creo i 3 pdf da mostrare
        //per efficienza i pdf vengono creati solo al primo avvio dell'app
        //e poi memorizzati nell'internal storage così l'app è più veloce dal secondo avvio
        String documento = "documento.pdf";
        String documento1 = "documento1.pdf";
        String documento2 = "documento2.pdf";

        pdfFile = new File(getFilesDir(), documento);
        pdfFile1 = new File(getFilesDir(), documento1);
        pdfFile2 = new File(getFilesDir(), documento2);
        if (!pdfFile.exists() && !pdfFile1.exists() && !pdfFile2.exists()) {
            //showToast("Il documento non esiste" + pdfFile.exists() + pdfFile1.exists() + pdfFile2.exists());
            creaPdf("documento.pdf");
            createPdf1("documento2.pdf");
            createPdf2("documento1.pdf");
        } else {

        }
        creaPdf("documento.pdf");
        createPdf1("documento2.pdf");
        createPdf2("documento1.pdf");

        btn = findViewById(R.id.pdf_button);

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, PdfActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (inputSource == InputSource.CAMERA) {
            // Restarts the camera and the opengl surface rendering.
            cameraInput = new CameraInput(this);
            cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
            glSurfaceView.post(this::startCamera);
            glSurfaceView.setVisibility(View.VISIBLE);
        } else if (inputSource == InputSource.VIDEO) {
            videoInput.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (inputSource == InputSource.CAMERA) {
            glSurfaceView.setVisibility(View.GONE);
            cameraInput.close();
        } else if (inputSource == InputSource.VIDEO) {
            videoInput.pause();
        }
    }

    private Bitmap downscaleBitmap(Bitmap originalBitmap) {
        double aspectRatio = (double) originalBitmap.getWidth() / originalBitmap.getHeight();
        int width = imageView.getWidth();
        int height = imageView.getHeight();
        if (((double) imageView.getWidth() / imageView.getHeight()) > aspectRatio) {
            width = (int) (height * aspectRatio);
        } else {
            height = (int) (width / aspectRatio);
        }
        return Bitmap.createScaledBitmap(originalBitmap, width, height, false);
    }

    private Bitmap rotateBitmap(Bitmap inputBitmap, InputStream imageData) throws IOException {
        int orientation =
                new ExifInterface(imageData)
                        .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        if (orientation == ExifInterface.ORIENTATION_NORMAL) {
            return inputBitmap;
        }
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            default:
                matrix.postRotate(0);
        }
        return Bitmap.createBitmap(
                inputBitmap, 0, 0, inputBitmap.getWidth(), inputBitmap.getHeight(), matrix, true);
    }

    /**
     * Sets up the UI components for the static image demo.
     */
    private void setupStaticImageDemoUiComponents() {
        // The Intent to access gallery and read images as bitmap.
        imageGetter =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> {
                            Intent resultIntent = result.getData();
                            if (resultIntent != null) {
                                if (result.getResultCode() == RESULT_OK) {
                                    Bitmap bitmap = null;
                                    try {
                                        bitmap =
                                                downscaleBitmap(
                                                        MediaStore.Images.Media.getBitmap(
                                                                this.getContentResolver(), resultIntent.getData()));
                                    } catch (IOException e) {
                                        Log.e(TAG, "Bitmap reading error:" + e);
                                    }
                                    try {
                                        InputStream imageData =
                                                this.getContentResolver().openInputStream(resultIntent.getData());
                                        bitmap = rotateBitmap(bitmap, imageData);
                                    } catch (IOException e) {
                                        Log.e(TAG, "Bitmap rotation error:" + e);
                                    }
                                    if (bitmap != null) {
                                        hands.send(bitmap);
                                    }
                                }
                            }
                        });
        Button loadImageButton = findViewById(R.id.button_load_picture);
        loadImageButton.setOnClickListener(
                v -> {
                    if (inputSource != InputSource.IMAGE) {
                        stopCurrentPipeline();
                        setupStaticImageModePipeline();
                    }
                    // Reads images from gallery.
                    Intent pickImageIntent = new Intent(Intent.ACTION_PICK);
                    pickImageIntent.setDataAndType(MediaStore.Images.Media.INTERNAL_CONTENT_URI, "image/*");
                    imageGetter.launch(pickImageIntent);
                });
        imageView = new HandsResultImageView(this);
    }

    /**
     * Sets up core workflow for static image mode.
     */
    private void setupStaticImageModePipeline() {
        this.inputSource = InputSource.IMAGE;
        // Initializes a new MediaPipe Hands solution instance in the static image mode.
        hands =
                new Hands(
                        this,
                        HandsOptions.builder()
                                .setStaticImageMode(true)
                                .setMaxNumHands(2)
                                .setRunOnGpu(RUN_ON_GPU)
                                .build());

        // Connects MediaPipe Hands solution to the user-defined HandsResultImageView.
        hands.setResultListener(
                handsResult -> {
                    //logWristLandmark(handsResult, /*showPixelValues=*/ true);
                    imageView.setHandsResult(handsResult);
                    runOnUiThread(() -> imageView.update());
                });
        hands.setErrorListener((message, e) -> Log.e(TAG, "MediaPipe Hands error:" + message));

        // Updates the preview layout.
        FrameLayout frameLayout = findViewById(R.id.preview_display_layout);
        frameLayout.removeAllViewsInLayout();
        imageView.setImageDrawable(null);
        frameLayout.addView(imageView);
        imageView.setVisibility(View.VISIBLE);
    }

    /**
     * Sets up the UI components for the video demo.
     */
    private void setupVideoDemoUiComponents() {
        // The Intent to access gallery and read a video file.
        videoGetter =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> {
                            Intent resultIntent = result.getData();
                            if (resultIntent != null) {
                                if (result.getResultCode() == RESULT_OK) {
                                    glSurfaceView.post(
                                            () ->
                                                    videoInput.start(
                                                            this,
                                                            resultIntent.getData(),
                                                            hands.getGlContext(),
                                                            glSurfaceView.getWidth(),
                                                            glSurfaceView.getHeight()));
                                }
                            }
                        });
        Button loadVideoButton = findViewById(R.id.button_load_video);
        loadVideoButton.setOnClickListener(
                v -> {
                    stopCurrentPipeline();
                    setupStreamingModePipeline(InputSource.VIDEO);
                    // Reads video from gallery.
                    Intent pickVideoIntent = new Intent(Intent.ACTION_PICK);
                    pickVideoIntent.setDataAndType(MediaStore.Video.Media.INTERNAL_CONTENT_URI, "video/*");
                    videoGetter.launch(pickVideoIntent);
                });
    }

    /**
     * Sets up the UI components for the live demo with camera input.
     */
    public void setupLiveDemoUiComponents() {
        Button startCameraButton = findViewById(R.id.button_start_camera);
        startCameraButton.setOnClickListener(
                v -> {
                    if (inputSource == InputSource.CAMERA) {
                        return;
                    }
                    stopCurrentPipeline();
                    setupStreamingModePipeline(InputSource.CAMERA);
                });
    }

    /**
     * Sets up core workflow for streaming mode.
     */
    private void setupStreamingModePipeline(InputSource inputSource) {
        HandsResultGlRenderer handsResultGlRenderer = new HandsResultGlRenderer();

        ThumbUpGesture thumbUpGesture = new ThumbUpGesture();
        PinchGesture pinchGesture = new PinchGesture();
        ScrollPageGesture scrollPageGesture = new ScrollPageGesture();
        CrabGesture crabGesture = new CrabGesture();
        FourGesture fourGesture = new FourGesture();
        ThreeGesture threeGesture = new ThreeGesture();

        TextView wristLog = new TextView(this);
        wristLog.setText(String.valueOf(handsResultGlRenderer.log));
        wristLog.setTextSize(18);
        wristLog.setTextColor(Color.RED);
        wristLog.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        TextView thumbUp = new TextView(this);
        thumbUp.setText("Thumb up");
        thumbUp.setTextSize(18);
        thumbUp.setTextColor(Color.RED);
        TextView pinch = new TextView(this);
        pinch.setText("Pinch");
        pinch.setTextSize(18);
        pinch.setTextColor(Color.RED);
        TextView scroll = new TextView(this);
        scroll.setText("Scroll");
        scroll.setTextSize(18);
        scroll.setTextColor(Color.RED);
        pinch.setTextColor(Color.RED);
        TextView crab = new TextView(this);
        crab.setText("Crab");
        crab.setTextSize(18);
        crab.setTextColor(Color.RED);
        TextView openHand = new TextView(this);
        openHand.setText("Open hand");
        openHand.setTextSize(18);
        openHand.setTextColor(Color.RED);
        TextView treHand = new TextView(this);
        treHand.setText("3 Hand");
        treHand.setTextSize(18);
        treHand.setTextColor(Color.RED);

        LinearLayout gestureChecksLayout = new LinearLayout(this);
        gestureChecksLayout.setOrientation(LinearLayout.VERTICAL);
        gestureChecksLayout.setGravity(Gravity.BOTTOM | Gravity.RIGHT);

        //aggiungere TextView varie a gestureChecksLayout
        //gestureChecksLayout.addView(wristLog);
        //gestureChecksLayout.addView(thumbUp);
        //gestureChecksLayout.addView(pinch);
        //gestureChecksLayout.addView(crab);
        //gestureChecksLayout.addView(openHand);
        //gestureChecksLayout.addView(treHand);
        //gestureChecksLayout.addView(scroll);

        this.inputSource = inputSource;
        // Initializes a new MediaPipe Hands solution instance in the streaming mode.
        hands =
                new Hands(
                        this,
                        HandsOptions.builder()
                                .setStaticImageMode(false)
                                .setMaxNumHands(2)
                                .setRunOnGpu(RUN_ON_GPU)
                                .build());
        hands.setErrorListener((message, e) -> Log.e(TAG, "MediaPipe Hands error:" + message));

        if (inputSource == InputSource.CAMERA) {
            cameraInput = new CameraInput(this);
            cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
        } else if (inputSource == InputSource.VIDEO) {
            videoInput = new VideoInput(this);
            videoInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
        }

        // Initializes a new Gl surface view with a user-defined HandsResultGlRenderer.
        glSurfaceView =
                new SolutionGlSurfaceView<>(this, hands.getGlContext(), hands.getGlMajorVersion());
        glSurfaceView.setSolutionResultRenderer(handsResultGlRenderer);
        glSurfaceView.setRenderInputImage(true);
        hands.setResultListener(
                handsResult -> {
                    //logWristLandmark(handsResult, /*showPixelValues=*/ false);
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            wristLog.setText(handsResultGlRenderer.log);
                            int scrollGestureCheck = scrollPageGesture.checGesture(handsResult.multiHandLandmarks());
                            boolean thumbUpGestureCheck = thumbUpGesture.checkGesture(handsResult);
                            boolean pinchGestureCheck = pinchGesture.checkGesture(handsResult);
                            boolean crabGestureCheck = crabGesture.checkGesture(handsResult);
                            boolean openHandCheck = fourGesture.checkGesture(handsResult);
                            boolean threeHandCheck = threeGesture.checkGesture(handsResult);
                            if (thumbUpGestureCheck) {
                                thumbUp.setTextColor(Color.GREEN);
                                //Intent intent1 = new Intent(MainActivity.this, PdfActivity.class);
                                //startActivity(intent1);
                            } else {
                                thumbUp.setTextColor(Color.RED);
                            }

                            if (pinchGestureCheck) {
                                pinch.setTextColor(Color.GREEN);
                            } else {
                                pinch.setTextColor(Color.RED);
                            }

                            if (scrollGestureCheck == 1) {
                                scroll.setTextColor(Color.GREEN);
                            } else if (scrollGestureCheck == 2) {
                                scroll.setTextColor(Color.YELLOW);
                            } else {
                                scroll.setTextColor(Color.RED);
                            }

                            if (crabGestureCheck) {
                                crab.setTextColor(Color.GREEN);
                            } else {
                                crab.setTextColor(Color.RED);
                            }

                            if (openHandCheck) {
                                openHand.setTextColor(Color.GREEN);
                            } else {
                                openHand.setTextColor(Color.RED);
                            }

                            if (threeHandCheck) {
                                treHand.setTextColor(Color.GREEN);
                            } else {
                                treHand.setTextColor(Color.RED);
                            }
                        }
                    });
                    glSurfaceView.setRenderData(handsResult);
                    glSurfaceView.requestRender();
                });


        // The runnable to start camera after the gl surface view is attached.
        // For video input source, videoInput.start() will be called when the video uri is available.
        if (inputSource == InputSource.CAMERA) {
            glSurfaceView.post(this::startCamera);
        }

        // Updates the preview layout.
        FrameLayout frameLayout = findViewById(R.id.preview_display_layout);
        imageView.setVisibility(View.GONE);
        frameLayout.removeAllViewsInLayout();
        frameLayout.addView(glSurfaceView);

        // add linear layout view to frame layout
        frameLayout.addView(gestureChecksLayout);

        glSurfaceView.setVisibility(View.VISIBLE);
        frameLayout.requestLayout();
    }

    private void startCamera() {
        cameraInput.start(
                this,
                hands.getGlContext(),
                CameraInput.CameraFacing.FRONT,
                glSurfaceView.getWidth(),
                glSurfaceView.getHeight());
    }

    private void stopCurrentPipeline() {
        if (cameraInput != null) {
            cameraInput.setNewFrameListener(null);
            cameraInput.close();
        }
        if (videoInput != null) {
            videoInput.setNewFrameListener(null);
            videoInput.close();
        }
        if (glSurfaceView != null) {
            glSurfaceView.setVisibility(View.GONE);
        }
        if (hands != null) {
            hands.close();
        }
    }

    private void logWristLandmark(HandsResult result, boolean showPixelValues) {
        if (result.multiHandLandmarks().isEmpty()) {
            return;
        }
        NormalizedLandmark wristLandmark =
                result.multiHandLandmarks().get(0).getLandmarkList().get(HandLandmark.WRIST);
        // For Bitmaps, show the pixel values. For texture inputs, show the normalized coordinates.
        if (showPixelValues) {
            int width = result.inputBitmap().getWidth();
            int height = result.inputBitmap().getHeight();
            Log.i(
                    TAG,
                    String.format(
                            "MediaPipe Hand wrist coordinates (pixel values): x=%f, y=%f",
                            wristLandmark.getX() * width, wristLandmark.getY() * height));
        } else {
            Log.i(
                    TAG,
                    String.format(
                            "MediaPipe Hand wrist normalized coordinates (value range: [0, 1]): x=%f, y=%f",
                            wristLandmark.getX(), wristLandmark.getY()));
        }
        if (result.multiHandWorldLandmarks().isEmpty()) {
            return;
        }
        Landmark wristWorldLandmark =
                result.multiHandWorldLandmarks().get(0).getLandmarkList().get(HandLandmark.WRIST);
        Log.i(
                TAG,
                String.format(
                        "MediaPipe Hand wrist world coordinates (in meters with the origin at the hand's"
                                + " approximate geometric center): x=%f m, y=%f m, z=%f m",
                        wristWorldLandmark.getX(), wristWorldLandmark.getY(), wristWorldLandmark.getZ()));
    }

    private void creaPdf(String documento) {
        pdfFile = new File(getFilesDir(), documento);
        if (!pdfFile.exists()) {
            showToast("Il documento non esiste");

        } else {
            showToast("Il documento esiste già");
        }
        Document document = new Document(PageSize.A4);
        try {
            PdfWriter.getInstance(document, new FileOutputStream(pdfFile));
            document.open();

            document.addTitle("Titolo");
            document.addAuthor("Autore");
            document.addSubject("Soggetto");
            document.addKeywords("Parole chiave");
            //sarebbe carino secondo me inserire la nostra relazione qui
            document.add(new Paragraph("Cristoforo Colombo, also known as Christopher Columbus, was an Italian explorer and navigator who completed four voyages across the Atlantic Ocean, opening the way for the widespread European exploration and colonization of the Americas.\n"));
            document.add(new Paragraph("Colombo was born in Genoa, Italy, in 1451. As a young man, he became a sailor and began making trips to the Mediterranean, eventually becoming a skilled navigator and cartographer.\n\n\n"));
            //inserisco una tabella per grafica
            PdfPTable table = new PdfPTable(3);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{0.25f, 0.5f, 0.25f});
            String[] header = {"Header 1", "Header 2", "Header 3"};
            for (String s : header) {
                table.addCell(s);
            }
            for (int i = 0; i < 10; i++) {
                table.addCell("Cell " + i);
                table.addCell("Cell " + i);
                table.addCell("Cell " + i);
            }
            document.add(table);
            document.add(new Paragraph("In 1477, he moved to Lisbon, Portugal, where he began seeking funding for his proposed voyage across the Atlantic. He made several unsuccessful attempts to secure sponsorship from the Portuguese court before eventually receiving backing from King Ferdinand and Queen Isabella of Spain in 1492.\n"));
            document.add(new Paragraph("With three ships, the Niña, the Pinta and the Santa Maria, Columbus set sail on August 3, 1492. After a difficult voyage, he landed in the Bahamas on October 12, 1492. He made three more voyages to the Americas, but he never set foot on mainland North America.\n"));
            document.add(new Paragraph("Despite the fact that Columbus did not actually discover the Americas, as they were already inhabited by indigenous peoples, his voyages did open the way for the widespread exploration and colonization of the Americas by Europeans, leading to the displacement and mistreatment of the native populations.\n"));
            document.add(new Paragraph("Today, Columbus is celebrated as a hero by some and criticized by others for his treatment of indigenous peoples. His legacy is a complex one and is still debated by historians and scholars.\n"));
            document.newPage();
            document.add(new Paragraph("Cristoforo Colombo, also known as Christopher Columbus, was an Italian explorer and navigator who completed four voyages across the Atlantic Ocean, opening the way for the widespread European exploration and colonization of the Americas.\n"));
            document.add(new Paragraph("Colombo was born in Genoa, Italy, in 1451. As a young man, he became a sailor and began making trips to the Mediterranean, eventually becoming a skilled navigator and cartographer.\n\n\n"));
            //inserisco una tabella per grafica
            PdfPTable table1 = new PdfPTable(3);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{0.25f, 0.5f, 0.25f});
            String[] header1 = {"Header 1", "Header 2", "Header 3"};
            for (String s : header1) {
                table1.addCell(s);
            }
            for (int i = 0; i < 10; i++) {
                table1.addCell("Cell " + i);
                table1.addCell("Cell " + i);
                table1.addCell("Cell " + i);
            }
            document.add(table1);
            document.add(new Paragraph("In 1477, he moved to Lisbon, Portugal, where he began seeking funding for his proposed voyage across the Atlantic. He made several unsuccessful attempts to secure sponsorship from the Portuguese court before eventually receiving backing from King Ferdinand and Queen Isabella of Spain in 1492.\n"));
            document.add(new Paragraph("With three ships, the Niña, the Pinta and the Santa Maria, Columbus set sail on August 3, 1492. After a difficult voyage, he landed in the Bahamas on October 12, 1492. He made three more voyages to the Americas, but he never set foot on mainland North America.\n"));
            document.add(new Paragraph("Despite the fact that Columbus did not actually discover the Americas, as they were already inhabited by indigenous peoples, his voyages did open the way for the widespread exploration and colonization of the Americas by Europeans, leading to the displacement and mistreatment of the native populations.\n"));
            document.add(new Paragraph("Today, Columbus is celebrated as a hero by some and criticized by others for his treatment of indigenous peoples. His legacy is a complex one and is still debated by historians and scholars.\n"));
            document.newPage();
            document.add(new Paragraph("Cristoforo Colombo, also known as Christopher Columbus, was an Italian explorer and navigator who completed four voyages across the Atlantic Ocean, opening the way for the widespread European exploration and colonization of the Americas.\n"));
            document.add(new Paragraph("Colombo was born in Genoa, Italy, in 1451. As a young man, he became a sailor and began making trips to the Mediterranean, eventually becoming a skilled navigator and cartographer.\n\n\n"));
            //inserisco una tabella per grafica
            PdfPTable table2 = new PdfPTable(3);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{0.25f, 0.5f, 0.25f});
            String[] header2 = {"Header 1", "Header 2", "Header 3"};
            for (String s : header2) {
                table2.addCell(s);
            }
            for (int i = 0; i < 10; i++) {
                table2.addCell("Cell " + i);
                table2.addCell("Cell " + i);
                table2.addCell("Cell " + i);
            }
            document.add(table2);
            document.add(new Paragraph("In 1477, he moved to Lisbon, Portugal, where he began seeking funding for his proposed voyage across the Atlantic. He made several unsuccessful attempts to secure sponsorship from the Portuguese court before eventually receiving backing from King Ferdinand and Queen Isabella of Spain in 1492.\n"));
            document.add(new Paragraph("With three ships, the Niña, the Pinta and the Santa Maria, Columbus set sail on August 3, 1492. After a difficult voyage, he landed in the Bahamas on October 12, 1492. He made three more voyages to the Americas, but he never set foot on mainland North America.\n"));
            document.add(new Paragraph("Despite the fact that Columbus did not actually discover the Americas, as they were already inhabited by indigenous peoples, his voyages did open the way for the widespread exploration and colonization of the Americas by Europeans, leading to the displacement and mistreatment of the native populations.\n"));
            document.add(new Paragraph("Today, Columbus is celebrated as a hero by some and criticized by others for his treatment of indigenous peoples. His legacy is a complex one and is still debated by historians and scholars.\n"));
            document.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return;
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void createPdf2(String documento) {
        pdfFile = new File(getFilesDir(), documento);
        Document document = new Document(PageSize.A4);
        try {
            PdfWriter.getInstance(document, new FileOutputStream(pdfFile));
            document.open();
            document.add(new Paragraph("Abraham Lincoln was the 16th President of the United States, serving from March 1861 until his assassination in April 1865. Lincoln led the United States through its Civil War—its bloodiest war and perhaps its greatest moral, constitutional, and political crisis. In doing so, he preserved the Union, abolished slavery, strengthened the federal government, and modernized the economy.\n"));
            document.add(new Paragraph("Born in Kentucky and raised in Indiana, Lincoln was primarily self-educated. He quickly rose to prominence as a Whig Party politician and as a lawyer. He served one term in the Illinois House of Representatives and was elected to the United States House of Representatives in 1846. In 1858, Lincoln ran for the United States Senate, but lost the race to Stephen A. Douglas. Lincoln's debates with Douglas during the campaign helped Lincoln become nationally known. Lincoln's speeches and debates, especially his \"House Divided\" speech, helped him gain the Republican Party nomination in 1860.\n"));
            document.add(new Paragraph("Lincoln's election in 1860 was the catalyst for the Southern states to secede from the Union, leading to the Civil War. Lincoln's primary goal as President was to preserve the Union, and he used all the powers of the presidency to do so. He issued the Emancipation Proclamation, which declared that all slaves in the Confederate states shall be then, thenceforward, and forever free. Lincoln also used his executive power to establish the first national draft, to increase the size of the army and navy, and to suspend the writ of habeas corpus, which allowed him to arrest and detain people without trial.\n"));
            document.add(new Paragraph("Despite Lincoln's efforts, the Civil War was a long and bloody conflict, with heavy casualties on both sides. Lincoln was criticized for his handling of the war and for his military strategy, but he remained steadfast in his determination to preserve the Union. In 1863, Lincoln delivered the Gettysburg Address, one of the most famous speeches in American history, in which he reaffirmed the principles of democracy and equality.\n\n\n"));
            //inserisco una tabella per grafica
            PdfPTable table = new PdfPTable(3);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{0.25f, 0.5f, 0.25f});
            String[] header = {"Header 1", "Header 2", "Header 3"};
            for (String s : header) {
                table.addCell(s);
            }
            for (int i = 0; i < 2; i++) {
                table.addCell("Cell " + i);
                table.addCell("Cell " + i);
                table.addCell("Cell " + i);
            }
            document.add(table);
            document.add(new Paragraph("In 1864, Lincoln was re-elected as President, and the tide of the war began to turn in favor of the Union. In April 1865, Confederate General Robert E. Lee surrendered his army to Union General Ulysses S. Grant, effectively ending the Civil War. However, Lincoln's celebration was short-lived, as he was assassinated just five days later by John Wilkes Booth, a Confederate sympathizer.\n"));
            document.add(new Paragraph("Lincoln's death was a great loss to the nation, as he had become a symbol of unity and strength during one of the most difficult periods in American history. Lincoln's presidency, and his leadership during the Civil War, had a profound impact on the United States. He preserved the Union, abolished slavery, strengthened the federal government, and modernized the economy.\n"));
            document.add(new Paragraph("He also left a lasting legacy through his speeches and writings, which continue to be studied and admired to this day.\n"));
            document.add(new Paragraph("In conclusion, Abraham Lincoln was a great leader who guided the United States through its Civil War and changed the course of American history. His leadership, determination and unwavering commitment to preserving the Union and abolishing slavery, will be remembered forever. He is considered as one of the greatest president of the United States for his actions and speeches that are still studied and admired today.\n"));
            document.newPage();
            document.add(new Paragraph("Abraham Lincoln was the 16th President of the United States, serving from March 1861 until his assassination in April 1865. Lincoln led the United States through its Civil War—its bloodiest war and perhaps its greatest moral, constitutional, and political crisis. In doing so, he preserved the Union, abolished slavery, strengthened the federal government, and modernized the economy.\n"));
            document.add(new Paragraph("Born in Kentucky and raised in Indiana, Lincoln was primarily self-educated. He quickly rose to prominence as a Whig Party politician and as a lawyer. He served one term in the Illinois House of Representatives and was elected to the United States House of Representatives in 1846. In 1858, Lincoln ran for the United States Senate, but lost the race to Stephen A. Douglas. Lincoln's debates with Douglas during the campaign helped Lincoln become nationally known. Lincoln's speeches and debates, especially his \"House Divided\" speech, helped him gain the Republican Party nomination in 1860.\n"));
            document.add(new Paragraph("Lincoln's election in 1860 was the catalyst for the Southern states to secede from the Union, leading to the Civil War. Lincoln's primary goal as President was to preserve the Union, and he used all the powers of the presidency to do so. He issued the Emancipation Proclamation, which declared that all slaves in the Confederate states \"shall be then, thenceforward, and forever free.\" Lincoln also used his executive power to establish the first national draft, to increase the size of the army and navy, and to suspend the writ of habeas corpus, which allowed him to arrest and detain people without trial.\n"));
            document.add(new Paragraph("Despite Lincoln's efforts, the Civil War was a long and bloody conflict, with heavy casualties on both sides. Lincoln was criticized for his handling of the war and for his military strategy, but he remained steadfast in his determination to preserve the Union. In 1863, Lincoln delivered the Gettysburg Address, one of the most famous speeches in American history, in which he reaffirmed the principles of democracy and equality.\n\n\n"));
            //inserisco una tabella per grafica
            PdfPTable table2 = new PdfPTable(3);
            table2.setWidthPercentage(100);
            table2.setWidths(new float[]{0.25f, 0.5f, 0.25f});
            String[] header2 = {"Header 1", "Header 2", "Header 3"};
            for (String s : header2) {
                table2.addCell(s);
            }
            for (int i = 0; i < 2; i++) {
                table2.addCell("Cell " + i);
                table2.addCell("Cell " + i);
                table2.addCell("Cell " + i);
            }
            document.add(table2);
            document.add(new Paragraph("In 1864, Lincoln was re-elected as President, and the tide of the war began to turn in favor of the Union. In April 1865, Confederate General Robert E. Lee surrendered his army to Union General Ulysses S. Grant, effectively ending the Civil War. However, Lincoln's celebration was short-lived, as he was assassinated just five days later by John Wilkes Booth, a Confederate sympathizer.\n"));
            document.add(new Paragraph("Lincoln's death was a great loss to the nation, as he had become a symbol of unity and strength during one of the most difficult periods in American history. Lincoln's presidency, and his leadership during the Civil War, had a profound impact on the United States. He preserved the Union, abolished slavery, strengthened the federal government, and modernized the economy.\n"));
            document.add(new Paragraph("He also left a lasting legacy through his speeches and writings, which continue to be studied and admired to this day.\n"));
            document.add(new Paragraph("In conclusion, Abraham Lincoln was a great leader who guided the United States through its Civil War and changed the course of American history. His leadership, determination and unwavering commitment to preserving the Union and abolishing slavery, will be remembered forever. He is considered as one of the greatest president of the United States for his actions and speeches that are still studied and admired today.\n"));
            document.newPage();
            document.add(new Paragraph("Abraham Lincoln was the 16th President of the United States, serving from March 1861 until his assassination in April 1865. Lincoln led the United States through its Civil War—its bloodiest war and perhaps its greatest moral, constitutional, and political crisis. In doing so, he preserved the Union, abolished slavery, strengthened the federal government, and modernized the economy.\n"));
            document.add(new Paragraph("Born in Kentucky and raised in Indiana, Lincoln was primarily self-educated. He quickly rose to prominence as a Whig Party politician and as a lawyer. He served one term in the Illinois House of Representatives and was elected to the United States House of Representatives in 1846. In 1858, Lincoln ran for the United States Senate, but lost the race to Stephen A. Douglas. Lincoln's debates with Douglas during the campaign helped Lincoln become nationally known. Lincoln's speeches and debates, especially his \"House Divided\" speech, helped him gain the Republican Party nomination in 1860.\n"));
            document.add(new Paragraph("Lincoln's election in 1860 was the catalyst for the Southern states to secede from the Union, leading to the Civil War. Lincoln's primary goal as President was to preserve the Union, and he used all the powers of the presidency to do so. He issued the Emancipation Proclamation, which declared that all slaves in the Confederate states \"shall be then, thenceforward, and forever free.\" Lincoln also used his executive power to establish the first national draft, to increase the size of the army and navy, and to suspend the writ of habeas corpus, which allowed him to arrest and detain people without trial.\n"));
            document.add(new Paragraph("Despite Lincoln's efforts, the Civil War was a long and bloody conflict, with heavy casualties on both sides. Lincoln was criticized for his handling of the war and for his military strategy, but he remained steadfast in his determination to preserve the Union. In 1863, Lincoln delivered the Gettysburg Address, one of the most famous speeches in American history, in which he reaffirmed the principles of democracy and equality.\n\n\n"));
            //inserisco una tabella per grafica
            PdfPTable table3 = new PdfPTable(3);
            table3.setWidthPercentage(100);
            table3.setWidths(new float[]{0.25f, 0.5f, 0.25f});
            String[] header3 = {"Header 1", "Header 2", "Header 3"};
            for (String s : header3) {
                table3.addCell(s);
            }
            for (int i = 0; i < 2; i++) {
                table3.addCell("Cell " + i);
                table3.addCell("Cell " + i);
                table3.addCell("Cell " + i);
            }
            document.add(table3);
            document.add(new Paragraph("In 1864, Lincoln was re-elected as President, and the tide of the war began to turn in favor of the Union. In April 1865, Confederate General Robert E. Lee surrendered his army to Union General Ulysses S. Grant, effectively ending the Civil War. However, Lincoln's celebration was short-lived, as he was assassinated just five days later by John Wilkes Booth, a Confederate sympathizer.\n"));
            document.add(new Paragraph("Lincoln's death was a great loss to the nation, as he had become a symbol of unity and strength during one of the most difficult periods in American history. Lincoln's presidency, and his leadership during the Civil War, had a profound impact on the United States. He preserved the Union, abolished slavery, strengthened the federal government, and modernized the economy. \n"));
            document.add(new Paragraph("He also left a lasting legacy through his speeches and writings, which continue to be studied and admired to this day.\n"));
            document.add(new Paragraph("In conclusion, Abraham Lincoln was a great leader who guided the United States through its Civil War and changed the course of American history. His leadership, determination and unwavering commitment to preserving the Union and abolishing slavery, will be remembered forever. He is considered as one of the greatest president of the United States for his actions and speeches that are still studied and admired today.\n"));
            document.newPage();
            document.add(new Paragraph("Abraham Lincoln was the 16th President of the United States, serving from March 1861 until his assassination in April 1865. Lincoln led the United States through its Civil War—its bloodiest war and perhaps its greatest moral, constitutional, and political crisis. In doing so, he preserved the Union, abolished slavery, strengthened the federal government, and modernized the economy.\n"));
            document.add(new Paragraph("Born in Kentucky and raised in Indiana, Lincoln was primarily self-educated. He quickly rose to prominence as a Whig Party politician and as a lawyer. He served one term in the Illinois House of Representatives and was elected to the United States House of Representatives in 1846. In 1858, Lincoln ran for the United States Senate, but lost the race to Stephen A. Douglas. Lincoln's debates with Douglas during the campaign helped Lincoln become nationally known. Lincoln's speeches and debates, especially his \"House Divided\" speech, helped him gain the Republican Party nomination in 1860.\n"));
            document.add(new Paragraph("Lincoln's election in 1860 was the catalyst for the Southern states to secede from the Union, leading to the Civil War. Lincoln's primary goal as President was to preserve the Union, and he used all the powers of the presidency to do so. He issued the Emancipation Proclamation, which declared that all slaves in the Confederate states \"shall be then, thenceforward, and forever free.\" Lincoln also used his executive power to establish the first national draft, to increase the size of the army and navy, and to suspend the writ of habeas corpus, which allowed him to arrest and detain people without trial.\n"));
            document.add(new Paragraph("Despite Lincoln's efforts, the Civil War was a long and bloody conflict, with heavy casualties on both sides. Lincoln was criticized for his handling of the war and for his military strategy, but he remained steadfast in his determination to preserve the Union. In 1863, Lincoln delivered the Gettysburg Address, one of the most famous speeches in American history, in which he reaffirmed the principles of democracy and equality.\n\n\n"));
            //inserisco una tabella per grafica
            PdfPTable table4 = new PdfPTable(3);
            table4.setWidthPercentage(100);
            table4.setWidths(new float[]{0.25f, 0.5f, 0.25f});
            String[] header4 = {"Header 1", "Header 2", "Header 3"};
            for (String s : header4) {
                table4.addCell(s);
            }
            for (int i = 0; i < 2; i++) {
                table4.addCell("Cell " + i);
                table4.addCell("Cell " + i);
                table4.addCell("Cell " + i);
            }
            document.add(table4);
            document.add(new Paragraph("In 1864, Lincoln was re-elected as President, and the tide of the war began to turn in favor of the Union. In April 1865, Confederate General Robert E. Lee surrendered his army to Union General Ulysses S. Grant, effectively ending the Civil War. However, Lincoln's celebration was short-lived, as he was assassinated just five days later by John Wilkes Booth, a Confederate sympathizer.\n"));
            document.add(new Paragraph("Lincoln's death was a great loss to the nation, as he had become a symbol of unity and strength during one of the most difficult periods in American history. Lincoln's presidency, and his leadership during the Civil War, had a profound impact on the United States. He preserved the Union, abolished slavery, strengthened the federal government, and modernized the economy.\n"));
            document.add(new Paragraph("He also left a lasting legacy through his speeches and writings, which continue to be studied and admired to this day.\n"));
            document.add(new Paragraph("In conclusion, Abraham Lincoln was a great leader who guided the United States through its Civil War and changed the course of American history. His leadership, determination and unwavering commitment to preserving the Union and abolishing slavery, will be remembered forever. He is considered as one of the greatest president of the United States for his actions and speeches that are still studied and admired today.\n"));
            document.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return;
    }

    private void createPdf1(String documento) {
        pdfFile = new File(getFilesDir(), documento);
        Document document = new Document(PageSize.A4);
        try {
            PdfWriter.getInstance(document, new FileOutputStream(pdfFile));
            document.open();
            //inserisco una tabella per grafica
            PdfPTable table = new PdfPTable(3);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{0.25f, 0.5f, 0.25f});
            String[] header = {"Header 1", "Header 2", "Header 3"};
            for (String s : header) {
                table.addCell(s);
            }
            for (int i = 0; i < 7; i++) {
                table.addCell("Cell " + i);
                table.addCell("Cell " + i);
                table.addCell("Cell " + i);
            }
            document.add(table);
            document.add(new Paragraph("The economic crisis of 2008, also known as the global financial crisis, was a severe financial downturn that affected economies around the world. It was triggered by the collapse of the housing market in the United States, which led to a crisis in the country's financial system. The crisis quickly spread to other parts of the world, causing severe economic downturns and high levels of unemployment.\n"));
            document.add(new Paragraph("The crisis had its roots in the United States, where a housing bubble had been building for several years. Banks and other financial institutions had been issuing large numbers of mortgages, many of which were given to people who could not afford them. These mortgages were then bundled together and sold as securities to investors around the world. However, when housing prices began to fall and many of these mortgages went into default, the value of these securities plummeted, causing huge losses for investors.\n"));
            document.add(new Paragraph("The crisis began to unfold in 2007, when the housing market in the United States began to collapse. As housing prices fell, many people found themselves unable to afford their mortgages and began to default on their loans. This led to a wave of foreclosures, which further depressed housing prices. Banks and other financial institutions that had invested heavily in the housing market saw the value of their assets decline, and many of them faced severe financial difficulties.\n"));
            document.add(new Paragraph("The crisis soon spread to other parts of the world, as banks and other financial institutions that had invested in the housing market in the United States also faced losses. In 2008, the crisis reached a critical point when Lehman Brothers, a major investment bank, declared bankruptcy. This event sent shockwaves through the global financial system, causing a rapid loss of confidence in banks and other financial institutions.\n"));
            document.add(new Paragraph("Governments around the world responded to the crisis by taking a variety of actions to stabilize the financial system and to boost economic growth. In the United States, the government created the Troubled Asset Relief Program (TARP) to provide financial assistance to banks and other financial institutions. Other countries also implemented similar programs to stabilize their own financial systems.\n"));
            document.add(new Paragraph("The economic crisis of 2008 had a severe impact on economies around the world. The United States, in particular, experienced a deep recession, with high levels of unemployment and slow economic growth. Other countries also faced similar economic downturns, and many people lost their jobs and their homes as a result of the crisis.\n"));
            document.add(new Paragraph("The economic crisis of 2008 had a severe impact on economies around the world. The United States, in particular, experienced a deep recession, with high levels of unemployment and slow economic growth. Other countries also faced similar economic downturns, and many people lost their jobs and their homes as a result of the crisis.\n"));
            document.newPage();
            document.add(new Paragraph("The economic crisis of 2008, also known as the global financial crisis, was a severe financial downturn that affected economies around the world. It was triggered by the collapse of the housing market in the United States, which led to a crisis in the country's financial system. The crisis quickly spread to other parts of the world, causing severe economic downturns and high levels of unemployment.\n"));
            document.add(new Paragraph("The crisis had its roots in the United States, where a housing bubble had been building for several years. Banks and other financial institutions had been issuing large numbers of mortgages, many of which were given to people who could not afford them. These mortgages were then bundled together and sold as securities to investors around the world. However, when housing prices began to fall and many of these mortgages went into default, the value of these securities plummeted, causing huge losses for investors.\n"));
            document.add(new Paragraph("The crisis began to unfold in 2007, when the housing market in the United States began to collapse. As housing prices fell, many people found themselves unable to afford their mortgages and began to default on their loans. This led to a wave of foreclosures, which further depressed housing prices. Banks and other financial institutions that had invested heavily in the housing market saw the value of their assets decline, and many of them faced severe financial difficulties.\n"));
            document.add(new Paragraph("The crisis soon spread to other parts of the world, as banks and other financial institutions that had invested in the housing market in the United States also faced losses. In 2008, the crisis reached a critical point when Lehman Brothers, a major investment bank, declared bankruptcy. This event sent shockwaves through the global financial system, causing a rapid loss of confidence in banks and other financial institutions.\n"));
            document.add(new Paragraph("Governments around the world responded to the crisis by taking a variety of actions to stabilize the financial system and to boost economic growth. In the United States, the government created the Troubled Asset Relief Program (TARP) to provide financial assistance to banks and other financial institutions. Other countries also implemented similar programs to stabilize their own financial systems.\n"));
            document.add(new Paragraph("The economic crisis of 2008 had a severe impact on economies around the world. The United States, in particular, experienced a deep recession, with high levels of unemployment and slow economic growth. Other countries also faced similar economic downturns, and many people lost their jobs and their homes as a result of the crisis.\n"));
            document.add(new Paragraph("The economic crisis of 2008 had a severe impact on economies around the world. The United States, in particular, experienced a deep recession, with high levels of unemployment and slow economic growth. Other countries also faced similar economic downturns, and many people lost their jobs and their homes as a result of the crisis.\n"));
            document.newPage();
            document.add(new Paragraph("The economic crisis of 2008, also known as the global financial crisis, was a severe financial downturn that affected economies around the world. It was triggered by the collapse of the housing market in the United States, which led to a crisis in the country's financial system. The crisis quickly spread to other parts of the world, causing severe economic downturns and high levels of unemployment.\n"));
            document.add(new Paragraph("The crisis had its roots in the United States, where a housing bubble had been building for several years. Banks and other financial institutions had been issuing large numbers of mortgages, many of which were given to people who could not afford them. These mortgages were then bundled together and sold as securities to investors around the world. However, when housing prices began to fall and many of these mortgages went into default, the value of these securities plummeted, causing huge losses for investors.\n"));
            document.add(new Paragraph("The crisis began to unfold in 2007, when the housing market in the United States began to collapse. As housing prices fell, many people found themselves unable to afford their mortgages and began to default on their loans. This led to a wave of foreclosures, which further depressed housing prices. Banks and other financial institutions that had invested heavily in the housing market saw the value of their assets decline, and many of them faced severe financial difficulties.\n"));
            document.add(new Paragraph("The crisis soon spread to other parts of the world, as banks and other financial institutions that had invested in the housing market in the United States also faced losses. In 2008, the crisis reached a critical point when Lehman Brothers, a major investment bank, declared bankruptcy. This event sent shockwaves through the global financial system, causing a rapid loss of confidence in banks and other financial institutions.\n"));
            document.add(new Paragraph("Governments around the world responded to the crisis by taking a variety of actions to stabilize the financial system and to boost economic growth. In the United States, the government created the Troubled Asset Relief Program (TARP) to provide financial assistance to banks and other financial institutions. Other countries also implemented similar programs to stabilize their own financial systems.\n"));
            document.add(new Paragraph("The economic crisis of 2008 had a severe impact on economies around the world. The United States, in particular, experienced a deep recession, with high levels of unemployment and slow economic growth. Other countries also faced similar economic downturns, and many people lost their jobs and their homes as a result of the crisis.\n"));
            document.add(new Paragraph("The economic crisis of 2008 had a severe impact on economies around the world. The United States, in particular, experienced a deep recession, with high levels of unemployment and slow economic growth. Other countries also faced similar economic downturns, and many people lost their jobs and their homes as a result of the crisis.\n"));
            document.newPage();
            document.add(new Paragraph("The economic crisis of 2008, also known as the global financial crisis, was a severe financial downturn that affected economies around the world. It was triggered by the collapse of the housing market in the United States, which led to a crisis in the country's financial system. The crisis quickly spread to other parts of the world, causing severe economic downturns and high levels of unemployment.\n"));
            document.add(new Paragraph("The crisis had its roots in the United States, where a housing bubble had been building for several years. Banks and other financial institutions had been issuing large numbers of mortgages, many of which were given to people who could not afford them. These mortgages were then bundled together and sold as securities to investors around the world. However, when housing prices began to fall and many of these mortgages went into default, the value of these securities plummeted, causing huge losses for investors.\n"));
            document.add(new Paragraph("The crisis began to unfold in 2007, when the housing market in the United States began to collapse. As housing prices fell, many people found themselves unable to afford their mortgages and began to default on their loans. This led to a wave of foreclosures, which further depressed housing prices. Banks and other financial institutions that had invested heavily in the housing market saw the value of their assets decline, and many of them faced severe financial difficulties.\n"));
            document.add(new Paragraph("The crisis soon spread to other parts of the world, as banks and other financial institutions that had invested in the housing market in the United States also faced losses. In 2008, the crisis reached a critical point when Lehman Brothers, a major investment bank, declared bankruptcy. This event sent shockwaves through the global financial system, causing a rapid loss of confidence in banks and other financial institutions.\n"));
            document.add(new Paragraph("Governments around the world responded to the crisis by taking a variety of actions to stabilize the financial system and to boost economic growth. In the United States, the government created the Troubled Asset Relief Program (TARP) to provide financial assistance to banks and other financial institutions. Other countries also implemented similar programs to stabilize their own financial systems.\n"));
            document.add(new Paragraph("The economic crisis of 2008 had a severe impact on economies around the world. The United States, in particular, experienced a deep recession, with high levels of unemployment and slow economic growth. Other countries also faced similar economic downturns, and many people lost their jobs and their homes as a result of the crisis.\n"));
            document.add(new Paragraph("The economic crisis of 2008 had a severe impact on economies around the world. The United States, in particular, experienced a deep recession, with high levels of unemployment and slow economic growth. Other countries also faced similar economic downturns, and many people lost their jobs and their homes as a result of the crisis.\n"));
            document.newPage();
            document.add(new Paragraph("The economic crisis of 2008, also known as the global financial crisis, was a severe financial downturn that affected economies around the world. It was triggered by the collapse of the housing market in the United States, which led to a crisis in the country's financial system. The crisis quickly spread to other parts of the world, causing severe economic downturns and high levels of unemployment.\n"));
            document.add(new Paragraph("The crisis had its roots in the United States, where a housing bubble had been building for several years. Banks and other financial institutions had been issuing large numbers of mortgages, many of which were given to people who could not afford them. These mortgages were then bundled together and sold as securities to investors around the world. However, when housing prices began to fall and many of these mortgages went into default, the value of these securities plummeted, causing huge losses for investors.\n"));
            document.add(new Paragraph("The crisis began to unfold in 2007, when the housing market in the United States began to collapse. As housing prices fell, many people found themselves unable to afford their mortgages and began to default on their loans. This led to a wave of foreclosures, which further depressed housing prices. Banks and other financial institutions that had invested heavily in the housing market saw the value of their assets decline, and many of them faced severe financial difficulties.\n"));
            document.add(new Paragraph("The crisis soon spread to other parts of the world, as banks and other financial institutions that had invested in the housing market in the United States also faced losses. In 2008, the crisis reached a critical point when Lehman Brothers, a major investment bank, declared bankruptcy. This event sent shockwaves through the global financial system, causing a rapid loss of confidence in banks and other financial institutions.\n"));
            document.add(new Paragraph("Governments around the world responded to the crisis by taking a variety of actions to stabilize the financial system and to boost economic growth. In the United States, the government created the Troubled Asset Relief Program (TARP) to provide financial assistance to banks and other financial institutions. Other countries also implemented similar programs to stabilize their own financial systems.\n"));
            document.add(new Paragraph("The economic crisis of 2008 had a severe impact on economies around the world. The United States, in particular, experienced a deep recession, with high levels of unemployment and slow economic growth. Other countries also faced similar economic downturns, and many people lost their jobs and their homes as a result of the crisis.\n"));
            document.add(new Paragraph("The economic crisis of 2008 had a severe impact on economies around the world. The United States, in particular, experienced a deep recession, with high levels of unemployment and slow economic growth. Other countries also faced similar economic downturns, and many people lost their jobs and their homes as a result of the crisis.\n"));
            document.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return;
    }
}
