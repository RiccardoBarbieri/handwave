package com.hands.gesture;

import android.util.Log;

import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.solutions.hands.HandsResult;
import com.hands.utils.HandPoints;
import com.hands.utils.TimedPoint;
import com.hands.utils.Utils;
import com.hands.utils.VectorUtils;

import java.util.ArrayList;
import java.util.List;

public class ScrollPageGesture implements IHandGesture {

    private static final String NAME = "SCROLL";
    private static final int GESTURE_ID = 3;

    private List<TimedPoint> lastTimedPoints = new ArrayList<TimedPoint>();
    private float last8landmarks = 0;
    private int puntaIndice = HandPoints.INDEX_TIP.getValue();


    public int scorrimento(List<LandmarkProto.NormalizedLandmark> landmarks, int error){

        List<TimedPoint> t = new ArrayList<TimedPoint>();
        TimedPoint temp = new TimedPoint();

        if (Utils.indiceMedioAlti(landmarks)){
            for (int i = 0; i <= 20; i++){
                t.add(i, temp.set(landmarks.get(i).getX(),landmarks.get(i).getY()));
            }

            if (!lastTimedPoints.isEmpty()){
                if (last8landmarks < 0.42 && last8landmarks > 0.22){
                    if ((lastTimedPoints.get(puntaIndice).velocityFrom(t.get(puntaIndice)))*10000 > 20 && landmarks.get(puntaIndice).getX() - last8landmarks > 0.2){
                        lastTimedPoints = t;
                        last8landmarks = landmarks.get(puntaIndice).getX();
                        return 1;

                    }
                }
                else if (last8landmarks > 0.58 && last8landmarks < 0.78){
                    if ((lastTimedPoints.get(puntaIndice).velocityFrom(t.get(puntaIndice)))*10000 > 20 && last8landmarks - landmarks.get(puntaIndice).getX() > 0.2){
                        lastTimedPoints = t;
                        last8landmarks = landmarks.get(puntaIndice).getX();
                        return 2;

                    }
                }
            }
            lastTimedPoints = t;
            last8landmarks = landmarks.get(puntaIndice).getX();
        }
        return 0;
    }


    public int checGesture(List<LandmarkProto.NormalizedLandmarkList> landmarkList) {
        if (landmarkList.size() > 0) {
            int errore = 8;
            //1 ->destra 2->sinistra

            List<Integer> errors = new ArrayList<>(2);
            errors.add(43);
            errors.add(43);
            List<LandmarkProto.NormalizedLandmark> pollice = new ArrayList<>();
            pollice.add(landmarkList.get(0).getLandmark(HandPoints.THUMB_BASE.getValue()));
            pollice.add(landmarkList.get(0).getLandmark(HandPoints.THUMB_LOWER.getValue()));
            pollice.add(landmarkList.get(0).getLandmark(HandPoints.THUMB_UPPER.getValue()));
            pollice.add(landmarkList.get(0).getLandmark(HandPoints.THUMB_TIP.getValue()));
            boolean checkPolliceStorto = !VectorUtils.checkInLineNormalized(pollice, errors);

            return scorrimento(landmarkList.get(0).getLandmarkList(), errore) - (checkPolliceStorto ? 10 : 0);
        }
        return -1;
    }

    @Override
    public boolean checkGesture(HandsResult sd) {
        return false;
    }

    @Override
    public String getName() {
        return this.NAME;
    }

    @Override
    public int getGestureId() {
        return this.GESTURE_ID;
    }

    @Override
    public GestureType getGestureType() {
        return GestureType.DYNAMIC;
    }
}