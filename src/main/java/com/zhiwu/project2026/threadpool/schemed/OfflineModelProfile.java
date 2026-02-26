package com.zhiwu.project2026.threadpool.schemed;

public record OfflineModelProfile(
        double coreBias,
        double qpsCoreWeight,
        double queueWaitCoreWeight,
        double execCoreWeight,
        double rejectionCoreWeight,
        double cpuCoreWeight,
        double xmxGbCoreWeight,
        double inverseReplicaCoreWeight,
        double queueBias,
        double qpsQueueWeight,
        double queueWaitQueueWeight,
        double execQueueWeight,
        double rejectionQueueWeight,
        double coreQueueWeight
) {
    public OfflineModelProfile {
        validateFinite(coreBias, "coreBias");
        validateFinite(qpsCoreWeight, "qpsCoreWeight");
        validateFinite(queueWaitCoreWeight, "queueWaitCoreWeight");
        validateFinite(execCoreWeight, "execCoreWeight");
        validateFinite(rejectionCoreWeight, "rejectionCoreWeight");
        validateFinite(cpuCoreWeight, "cpuCoreWeight");
        validateFinite(xmxGbCoreWeight, "xmxGbCoreWeight");
        validateFinite(inverseReplicaCoreWeight, "inverseReplicaCoreWeight");
        validateFinite(queueBias, "queueBias");
        validateFinite(qpsQueueWeight, "qpsQueueWeight");
        validateFinite(queueWaitQueueWeight, "queueWaitQueueWeight");
        validateFinite(execQueueWeight, "execQueueWeight");
        validateFinite(rejectionQueueWeight, "rejectionQueueWeight");
        validateFinite(coreQueueWeight, "coreQueueWeight");
    }

    private static void validateFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }
}
