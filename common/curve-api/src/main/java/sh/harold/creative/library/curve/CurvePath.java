package sh.harold.creative.library.curve;

import sh.harold.creative.library.spatial.Bounds3;
import sh.harold.creative.library.spatial.Vec3;

import java.util.List;

public interface CurvePath {

    Vec3 pointAtProgress(double progress);

    Vec3 pointAtDistance(double distance);

    Vec3 tangentAtProgress(double progress);

    Vec3 tangentAtDistance(double distance);

    double length();

    Bounds3 bounds();

    CurveSplit split(double progress);

    CurvePath trim(double fromProgress, double toProgress);

    CurvePath reverse();

    List<Vec3> resampleEvenly(int sampleCount);
}
