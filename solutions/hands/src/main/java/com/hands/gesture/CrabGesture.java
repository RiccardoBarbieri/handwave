package com.hands.gesture;

import android.util.Log;

import com.google.mediapipe.formats.proto.LandmarkProto;
import com.hands.utils.Constants;
import com.hands.utils.HandPoints;
import com.hands.utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CrabGesture implements IHandGesture {//qui lavoriamo solo su coordinate bidimensionali

    private static final String NAME = "Crab";
    private static final int GESTURE_ID = 3;
    private ArrayList<Float> delta;//x e y
    private ArrayList<Float> puntiPrec;//x e y del frame precedente
    private boolean firstTime;

    public CrabGesture(){
        firstTime=false;
        delta = new ArrayList<Float>();
        puntiPrec = new ArrayList<Float>();
        delta.add(0,0f);//x
        delta.add(1,0f);//y
        puntiPrec.add(0,0f);
        puntiPrec.add(1,0f);
    }

    @Override
    public boolean checkGesture(List<LandmarkProto.LandmarkList> landmarkList) {
        if (landmarkList.size() > 0) {


            int range = 5;

            float puntoA_X = landmarkList.get(0).getLandmark(HandPoints.THUMB_TIP.getValue()).getX();
            float puntoA_Y = landmarkList.get(0).getLandmark(HandPoints.THUMB_TIP.getValue()).getY();
            float puntoB_X = landmarkList.get(0).getLandmark(HandPoints.INDEX_TIP.getValue()).getX();
            float puntoB_Y = landmarkList.get(0).getLandmark(HandPoints.INDEX_TIP.getValue()).getY();
            boolean flagCrab = Utils.getXYDistanceInLevels(Constants.NUMERO_LIVELLI,landmarkList.get(0),puntoA_X,puntoA_Y,puntoB_X,puntoB_Y)<=range;


            if(firstTime==false){
                firstTime=true;
                puntiPrec.add(0,puntoA_X);
                puntiPrec.add(1,puntoA_Y);
            }



            if(flagCrab){
                delta.add(0,puntoA_X-puntiPrec.get(0));//x
                delta.add(1,puntoA_Y-puntiPrec.get(1));//y   //qui delta rappresenta un vettore
                //Log.println(Log.DEBUG,"debugg",delta.get(0)*10000+" "+ delta.get(1)*10000);
            }else{
                this.clearDelta();
            }



            return flagCrab;
        }
        return false;
    }

    public ArrayList<Float> getPuntiPrec(){
        return this.puntiPrec;
    }

    public ArrayList<Float> getDelta(){
        return delta;
    }
    public float getVettoreX(){
        if(delta.size()>2) {
            float temp = delta.get(0);
            Log.println(Log.DEBUG,"debugg","x "+delta.get(0)*10000);
            //delta.add(0,0f);
            return temp;
        }else{
            return 0;
        }
    }
    public float getVettoreY(){
        if(delta.size()>2) {
            float temp = delta.get(1);
            Log.println(Log.DEBUG,"debugg","y "+delta.get(1)*10000);
            //delta.add(1,0f);
            return temp;
        }else{
            return 0;
        }
    }

    public void clearDelta(){
        delta.clear();
        firstTime=false;
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
        return GestureType.STATIC;
    }
}