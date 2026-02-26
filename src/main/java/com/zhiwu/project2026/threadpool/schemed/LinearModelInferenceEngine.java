package com.zhiwu.project2026.threadpool.schemed;

import java.util.Objects;

public class LinearModelInferenceEngine implements ModelInferenceEngine {

    private static final double BYTES_PER_GB = 1024d * 1024d * 1024d;
    private final OfflineModelProfile profile;

    public LinearModelInferenceEngine(OfflineModelProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    @Override
    public ModelRecommendation infer(ModelFeatures features) {
        Objects.requireNonNull(features, "features");

        double xmxGb = features.xmxBytes() / BYTES_PER_GB;
        double inverseReplica = 1d / features.replicaCount();

        double rawCore = profile.coreBias()
                + features.qps() * profile.qpsCoreWeight()
                + features.queueWaitP95Ms() * profile.queueWaitCoreWeight()
                + features.execTimeP95Ms() * profile.execCoreWeight()
                + features.rejectionRate() * profile.rejectionCoreWeight()
                + features.availableProcessors() * profile.cpuCoreWeight()
                + xmxGb * profile.xmxGbCoreWeight()
                + inverseReplica * profile.inverseReplicaCoreWeight();
        int core = Math.max(1, safeRoundToInt(rawCore));

        double rawQueue = profile.queueBias()
                + features.qps() * profile.qpsQueueWeight()
                + features.queueWaitP95Ms() * profile.queueWaitQueueWeight()
                + features.execTimeP95Ms() * profile.execQueueWeight()
                + features.rejectionRate() * profile.rejectionQueueWeight()
                + core * profile.coreQueueWeight();
        int queue = Math.max(1, safeRoundToInt(rawQueue));

        return new ModelRecommendation(core, queue, rawCore, rawQueue);
    }

    private int safeRoundToInt(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("model score must be finite");
        }
        long rounded = Math.round(value);
        if (rounded > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (rounded < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) rounded;
    }
}
