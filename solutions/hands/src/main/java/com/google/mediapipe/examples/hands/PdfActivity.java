package com.google.mediapipe.examples.hands;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.exifinterface.media.ExifInterface;

import com.github.barteksc.pdfviewer.PDFView;
import com.google.mediapipe.solutioncore.CameraInput;
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView;
import com.google.mediapipe.solutioncore.VideoInput;
import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsOptions;
import com.google.mediapipe.solutions.hands.HandsResult;
import com.hands.gesture.ThumbUpGesture;
import com.itextpdf.text.Document;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import org.xml.sax.InputSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

// per relazione ricordarsi di aggiungere le dipendenze -> iText per creare il pdf e pdfviewer per visualizzarlo

public class PdfActivity extends AppCompatActivity {

    Button zoomIn, zoomOut, share, scrollDown;
    private Hands hands;
    private VideoInput videoInput;
    private ActivityResultLauncher<Intent> videoGetter;
    private CameraInput cameraInput;
    private HandsResultImageView imageView;
    private File pdfFile;
    private PDFView pdfView;

    private SolutionGlSurfaceView<HandsResult> glSurfaceView;

    // Run the pipeline and the model inference on GPU or CPU.
    private static final boolean RUN_ON_GPU = true;

    private enum InputSource2 {
        UNKNOWN,
        IMAGE,
        VIDEO,
        CAMERA,
    }
    private ActivityResultLauncher<Intent> imageGetter;
    private InputSource2 inputSource2 = InputSource2.UNKNOWN;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf);

        //copiando MainActivity.java
        setupStaticImageDemoUiComponents2();
        setupVideoDemoUiComponents2();
        setupLiveDemoUiComponents2();
        //inizializzazione bottoni da eliminare quando colleghiamo le gesture
        zoomIn = findViewById(R.id.zoom_in);
        zoomOut = findViewById(R.id.zoom_out);
        share = findViewById(R.id.condividi);
        scrollDown = findViewById(R.id.scroll_down);


        pdfView = (PDFView) findViewById(R.id.pdf_viewer);
        final LinearLayout buttonBar = findViewById(R.id.button_bar);

        //caricamento del pdf
        pdfFile = new File(getFilesDir(), "documento.pdf");
        createPDF("documento.pdf");
        pdfView.fromFile(pdfFile)
                .enableSwipe(true)
                .swipeHorizontal(false)
                .enableDoubletap(true)
                .defaultPage(0)
                .load();



        //collego bottoni a funzioni
        zoomIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float currentZoom = pdfView.getScaleX();
                pdfView.setScaleX(currentZoom + 0.25f);
                pdfView.setScaleY(currentZoom + 0.25f);
                buttonBar.bringToFront();
                buttonBar.setBackgroundColor(Color.WHITE);
            }
        });

        zoomOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                float currentZoom = pdfView.getScaleX();
                pdfView.setScaleX(currentZoom - 0.25f);
                pdfView.setScaleY(currentZoom - 0.25f);
                buttonBar.bringToFront();
                buttonBar.setBackgroundColor(Color.WHITE);
            }
        });

        share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent emailIntent = new Intent(Intent.ACTION_SEND);
                emailIntent.setType("text/plain");
                emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"destinatario@example.com"});
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Oggetto email");
                emailIntent.putExtra(Intent.EXTRA_TEXT, "Body email");
                emailIntent.putExtra(Intent.EXTRA_STREAM, pdfFile);
                startActivity(Intent.createChooser(emailIntent, "Invia email..."));
            }
        });
        scrollDown.setOnClickListener(v -> pdfView.jumpTo(pdfView.getPageAtPositionOffset(0) , true));


    }

    /*
        FUNZIONI PER MESSAGGIO E CREAZIONE PDF
     */
    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void createPDF(String documento){
        pdfFile = new File(getFilesDir(), documento);
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





    private Bitmap downscaleBitmap2(Bitmap originalBitmap) {
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

    private Bitmap rotateBitmap2(Bitmap inputBitmap, InputStream imageData) throws IOException {
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

    /*
        FUNZIONI PER SET UP UI COMPONENTS PER STATIC IMAGE DEMO
     */
    private void setupStaticImageDemoUiComponents2() {
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
                                                downscaleBitmap2(
                                                        MediaStore.Images.Media.getBitmap(
                                                                this.getContentResolver(), resultIntent.getData()));
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    try {
                                        InputStream imageData =
                                                this.getContentResolver().openInputStream(resultIntent.getData());
                                        bitmap = rotateBitmap2(bitmap, imageData);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    if (bitmap != null) {
                                        hands.send(bitmap);
                                    }
                                }
                            }
                        });
        imageView = new HandsResultImageView(this);
    }

    private void setupStaticImageModePipeline2() {
        this.inputSource2 = InputSource2.IMAGE;
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
                    imageView.setHandsResult(handsResult);
                    runOnUiThread(() -> imageView.update());
                });

        // Updates the preview layout.
        FrameLayout frameLayout = findViewById(R.id.frame_layout);
        frameLayout.removeAllViewsInLayout();
        imageView.setImageDrawable(null);
        frameLayout.addView(imageView);
        imageView.setVisibility(View.VISIBLE);
    }





    /*
        FUNZIONI PER SET UP UI COMPONENTS PER STATIC VIDEO DEMO
     */
    private void setupVideoDemoUiComponents2() {
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
                    stopCurrentPipeline2();
                    setupStreamingModePipeline2(InputSource2.VIDEO);
    }

    private void stopCurrentPipeline2(){
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

    private void setupStreamingModePipeline2(InputSource2 inputSource2){
        HandsResultGlRenderer handsResultGlRenderer = new HandsResultGlRenderer();
        ThumbUpGesture thumbUpGesture = new ThumbUpGesture();

        this.inputSource2 = inputSource2.VIDEO;
        // Initializes a new MediaPipe Hands solution instance in the streaming mode.
        hands =
                new Hands(
                        this,
                        HandsOptions.builder()
                                .setStaticImageMode(false)
                                .setMaxNumHands(2)
                                .setRunOnGpu(RUN_ON_GPU)
                                .build());

        if (inputSource2 == InputSource2.CAMERA) {
            cameraInput = new CameraInput(this);
            cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
        } else if (inputSource2 == inputSource2.VIDEO) {
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
                    this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if(thumbUpGesture.checkGesture(handsResult.multiHandWorldLandmarks())){
                                pdfView.jumpTo(pdfView.getPageAtPositionOffset(0) , true);
                                showToast("thumb up");
                            }
                            //thumbUpGesture.checkGesture(handsResult.multiHandWorldLandmarks());


                        }
                    });
                    glSurfaceView.setRenderData(handsResult);
                    glSurfaceView.requestRender();
                });


        // The runnable to start camera after the gl surface view is attached.
        // For video input source, videoInput.start() will be called when the video uri is available.
        if (inputSource2 == InputSource2.CAMERA) {
            glSurfaceView.post(this::startCamera);
        }

        // Updates the preview layout.
        FrameLayout frameLayout = findViewById(R.id.frame_layout);
        imageView.setVisibility(View.GONE);
        frameLayout.removeAllViewsInLayout();
        frameLayout.addView(glSurfaceView);

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




    /*
        FUNZIONI PER SET UP UI COMPONENTS PER live DEMO with CAMERA input
     */
    private void setupLiveDemoUiComponents2(){
        if (inputSource2 == InputSource2.CAMERA) {
            return;
        }
        stopCurrentPipeline2();
        setupStreamingModePipeline2(InputSource2.CAMERA);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (inputSource2 == InputSource2.CAMERA) {
            // Restarts the camera and the opengl surface rendering.
            cameraInput = new CameraInput(this);
            cameraInput.setNewFrameListener(textureFrame -> hands.send(textureFrame));
            glSurfaceView.post(this::startCamera);
            glSurfaceView.setVisibility(View.VISIBLE);
        } else if (inputSource2 == InputSource2.VIDEO) {
            videoInput.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (inputSource2 == InputSource2.CAMERA) {
            glSurfaceView.setVisibility(View.GONE);
            cameraInput.close();
        } else if (inputSource2 == InputSource2.VIDEO) {
            videoInput.pause();
        }
    }



}