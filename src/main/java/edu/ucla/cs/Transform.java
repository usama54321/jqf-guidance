package edu.ucla.cs;

import java.util.Random;

import org.datavec.image.transform.ImageTransform;
import org.datavec.image.transform.PipelineImageTransform;
import org.datavec.image.transform.CropImageTransform;
import org.datavec.image.transform.RotateImageTransform;
import org.datavec.image.transform.ScaleImageTransform;
import org.datavec.image.transform.FlipImageTransform;

public class Transform {
    int CROP_TRANSFORM_THRESHOLD = 10;
    int MAX_ROTATION = 360;
    int MAX_SCALE_X = 2;
    int MAX_SCALE_Y = 2;

    Random random;

    Transform(Random r) {
        this.random = r;
    }

    ImageTransform[] transforms = {
        new CropImageTransform(CROP_TRANSFORM_THRESHOLD),
        new RotateImageTransform(this.random, MAX_ROTATION),
        new ScaleImageTransform(this.random, MAX_SCALE_X, MAX_SCALE_Y),
        new FlipImageTransform(this.random)
    };

    PipelineImageTransform getTransformPipeline() {
        return new PipelineImageTransform(transforms);
    }
};
