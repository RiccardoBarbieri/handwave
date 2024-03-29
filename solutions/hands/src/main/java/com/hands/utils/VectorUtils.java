package com.hands.utils;


import com.google.mediapipe.formats.proto.LandmarkProto;

import java.util.List;

public class VectorUtils {

    static {
        System.loadLibrary("hands");
    }

    public static native double dotProductWrap(double[] v1, double[] v2);
    public static native double distanceToLineWrap(double[] v1, double[] v2, double[] v3);

    public static boolean checkInLineNormalized(List<LandmarkProto.NormalizedLandmark> landmarks, List<Integer> errors) {
        if (landmarks.size() - errors.size() != 2) {
            return false;
        }
        if (landmarks.size() < 3) {
            return false;
        }


        LandmarkProto.NormalizedLandmark first = landmarks.get(0);
        LandmarkProto.NormalizedLandmark last = landmarks.get(landmarks.size() - 1);

        for (LandmarkProto.NormalizedLandmark l : landmarks.subList(1, landmarks.size() - 1)) {
            LandmarkProto.NormalizedLandmark prev = landmarks.get(landmarks.indexOf(l) - 1);
            if (levelsToLine(Constants.NUMERO_LIVELLI, prev, l, first, last) > errors.get(landmarks.indexOf(l) - 1)) {
                return false;
            }
        }

        return true;
    }

    public static int levelsToLine(int numLevels, LandmarkProto.Landmark prevPhalanx, LandmarkProto.Landmark landmark, LandmarkProto.Landmark landmark1, LandmarkProto.Landmark landmark2) {
        double distance = distanceToLine(landmark, landmark1, landmark2);

        double distanzaMassima = Utils.getLandmarkDistance(prevPhalanx, landmark);

        return (int) Math.round((distance * numLevels) / distanzaMassima);
    }

    public static int levelsToLine(int numLevels, LandmarkProto.NormalizedLandmark prevPhalanx, LandmarkProto.NormalizedLandmark landmark, LandmarkProto.NormalizedLandmark landmark1, LandmarkProto.NormalizedLandmark landmark2) {
        double distance = distanceToLine(landmark, landmark1, landmark2);

        double distanzaMassima = Utils.getLandmarkDistance(prevPhalanx, landmark);

        return (int) Math.round((distance * numLevels) / distanzaMassima);
    }

    public static double distanceToLine(LandmarkProto.Landmark landmark, LandmarkProto.Landmark landmark1, LandmarkProto.Landmark landmark2) {
        double[] point1 = new double[]{landmark1.getX(), landmark1.getY(), landmark1.getZ()};
        double[] point2 = new double[]{landmark2.getX(), landmark2.getY(), landmark2.getZ()};
        double[] point = new double[]{landmark.getX(), landmark.getY(), landmark.getZ()};

        return VectorUtils.distanceToLineWrap(point, point1, point2);

        //double[] v1 = new double[]{landmark2.getX() - landmark1.getX(), landmark2.getY() - landmark1.getY(), landmark2.getZ() - landmark1.getZ()};
        //double[] v2 = new double[]{landmark.getX() - landmark1.getX(), landmark.getY() - landmark1.getY(), landmark.getZ() - landmark1.getZ()};
        //double scalar = dotProduct(v1, v2) / dotProduct(v1, v1);
        //double[] projection = new double[]{v1[0] * scalar, v1[1] * scalar, v1[2] * scalar};
        //double[] distanceVector = new double[]{v2[0] - projection[0], v2[1] - projection[1], v2[2] - projection[2]};
        //double distance = Math.sqrt(distanceVector[0] * distanceVector[0] + distanceVector[1] * distanceVector[1] + distanceVector[2] * distanceVector[2]);
        ////return distance
        ////temp
        ////truncate return value to 7 decimal places
        //return Math.round(distance * 10000000.0) / 10000000.0;
    }

    public static double distanceToLine(LandmarkProto.NormalizedLandmark landmark, LandmarkProto.NormalizedLandmark landmark1, LandmarkProto.NormalizedLandmark landmark2) {
        double[] point1 = new double[]{landmark1.getX(), landmark1.getY(), landmark1.getZ()};
        double[] point2 = new double[]{landmark2.getX(), landmark2.getY(), landmark2.getZ()};
        double[] point = new double[]{landmark.getX(), landmark.getY(), landmark.getZ()};

        return VectorUtils.distanceToLineWrap(point, point1, point2);

        //double[] v1 = new double[]{landmark2.getX() - landmark1.getX(), landmark2.getY() - landmark1.getY(), landmark2.getZ() - landmark1.getZ()};
        //double[] v2 = new double[]{landmark.getX() - landmark1.getX(), landmark.getY() - landmark1.getY(), landmark.getZ() - landmark1.getZ()};
        //
        //double scalar = dotProduct(v1, v2) / dotProduct(v1, v1);
        //double[] projection = new double[]{v1[0] * scalar, v1[1] * scalar, v1[2] * scalar};
        //double[] distanceVector = new double[]{v2[0] - projection[0], v2[1] - projection[1], v2[2] - projection[2]};
        //double distance1 = Math.sqrt(distanceVector[0] * distanceVector[0] + distanceVector[1] * distanceVector[1] + distanceVector[2] * distanceVector[2]);
        ////return distance
        ////temp
        ////truncate return value to 7 decimal places
        //double distance_rounded = Math.round(distance1 * 10000000.0) / 10000000.0;
    }

    private static double dotProduct(double[] v1, double[] v2) {
        return VectorUtils.dotProductWrap(v1, v2);
        //return v1[0] * v2[0] + v1[1] * v2[1] + v1[2] * v2[2];
    }

}
