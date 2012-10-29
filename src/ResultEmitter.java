import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Code for collecting and emitting benchmark results.
 */
public class ResultEmitter {
  /**
   * Minimum value to which we pin timings when recording them in a histogram: one microsecond.
   */
  public final static double MIN_HIST_VALUE_NS = 1E3;
  
  /**
   * Maximum value to which we pin timings when recording them in a histogram: ten seconds.
   */
  public final static double MAX_HIST_VALUE_NS = 1E10;
  
  /**
   * Spacing between our logarithmic histogram buckets.
   */
  public final static double HISTOGRAM_BUCKET_RATIO = 1.1;
  
  /**
   * nanoTime() when the benchmark run began.
   */
  public static long startTimeNs;
  
  /**
   * Time per output histogram, in nanoseconds. If we are not collecting individual histograms,
   * holds -1.
   */
  public static long timeBucketNs;
  
  /**
   * True to output each operation result as it occurs.
   */
  public static boolean outputRawResults;
  
  /**
   * Maps operation "signature" to the aggregated results of all operations with
   * that signature. A signature is of the form "operation,size,alignment", e.g.
   * "read,64K,4K". (Note that thread count does not appear as part of the signature.)
   */
  public static Map<String, OperationInfo> perOperationResults = new HashMap<String, OperationInfo>();
  
  /**
   * Initialize for the given command-line options. We look for the following options:
   * 
   *   --h=NNN: output NNN time bucket histograms.
   *   --raw: output the raw results. Defaults to true unless -h is specified.
   *   
   * @param durationSecs Duration of the benchmark run, in seconds.
   */
  public static void initialize(OptionParser options, int durationSecs) {
    perOperationResults.clear();
    
    startTimeNs = System.nanoTime();
    timeBucketNs = -1;
    outputRawResults = true;
    
    Integer histogramCount = options.parseInt("h");
    if (histogramCount != null) {
      timeBucketNs = (durationSecs * 1000000000L + histogramCount - 1) / histogramCount;
      outputRawResults = options.options.containsKey("raw");
    }
  }
  
  /**
   * Record the outcome of an operation.
   * 
   * @param signature The operation signature (e.g. "read,64K,4K"; see perOperationResults).
   * @param timestampNs nanoTime() value when the operation began.
   * @param durationNs Time, in nanoseconds, the operation took to complete.
   * @param isError True if the operation failed.
   */
  public static synchronized void recordOperation(String signature, long timestampNs, long durationNs, boolean isError) {
    OperationInfo operationInfo = perOperationResults.get(signature);
    if (operationInfo == null) {
      operationInfo = new OperationInfo(signature); 
      perOperationResults.put(signature, operationInfo);
    }
    
    operationInfo.recordOperation(timestampNs, durationNs, isError);
  }
  
  /**
   * Log a final summary of the run.
   */
  public static void emitFinalStats() {
    // Emit the final time bucket for each operation type.
    if (timeBucketNs > 0) {
      for (String signature : perOperationResults.keySet()) {
        OperationInfo opInfo = perOperationResults.get(signature);
        if (opInfo.timeBuckets.size() > 0) {
          int bucketIndex = opInfo.timeBuckets.size() - 1;
          Histogram finalBucket = opInfo.timeBuckets.get(bucketIndex);
          if (finalBucket.getCount() > 0) {
            logHistogram("Histogram " + bucketIndex + " for [" + signature + "]", finalBucket);
          }
        }
      }
    }
    
    // Emit a summary for each operation type.
    for (String signature : perOperationResults.keySet())
      logHistogram("Total for [" + signature + "]", perOperationResults.get(signature).total);
  }
  
  /**
   * Log a JSON object containing a complete description of this run -- its parameters and
   * results.
   */
  public static void emitJsonDump(int durationSeconds, ThreadSpec[] threadSpecs, long fileLen) {
    // Record parameters for this run.
    JsonObject jsonRoot = new JsonObject()
        .set("launchTime",     startTimeNs / 1000000000)
        .set("runtime",        durationSeconds)
        .set("bucketDuration", timeBucketNs / 1000000000)
        .set("fileSize",       fileLen)
        ;
    
    // Add information per "signature" (operation parameters).
    JsonArray operations = new JsonArray();
    jsonRoot.put("operations", operations);
    
    for (String signature : perOperationResults.keySet()) {
      OperationInfo operationInfo = perOperationResults.get(signature);
      
      // Count the number of threads with this signature.
      double threadCount = 0;
      for (ThreadSpec threadSpec : threadSpecs) {
        if (threadSpec == null) continue;
        if (threadSpec.getSignature().equals(signature))
          threadCount += threadSpec.threadCount;
      }
      
      JsonArray bucketJson = new JsonArray();
      for (Histogram bucket : operationInfo.timeBuckets)
        bucketJson.add(bucket.toJson());
      
      JsonObject operationJson = new JsonObject()
          .set("threadCount", threadCount)
          .set("signature", signature)
          .set("total", operationInfo.total.toJson())
          .set("timeBuckets", bucketJson);
          ;
      
      operations.add(operationJson);
    }
    
    // Write it all to the log.
    System.out.println("<begin json dump>");
    System.out.println(jsonRoot.toJson());
    System.out.println("<end json dump>");
  }
  
  /**
   * Information that we track per unique operation type (e.g. 4k aligned reads or 64k unaligned writes).
   */
  public static class OperationInfo {
    public final String signature;
    
    /**
     * Histogram for all operations.
     */
    public final Histogram total = new Histogram(MIN_HIST_VALUE_NS, MAX_HIST_VALUE_NS, HISTOGRAM_BUCKET_RATIO);
    
    /**
     * Histogram per timeBucketNs period. We add new histograms to the end of this list as time advances.
     * Unused if not recording bucketed histograms (i.e. if timeBucketNs is -1).
     */
    public final List<Histogram> timeBuckets = new ArrayList<Histogram>();

    public OperationInfo(String signature) {
      this.signature = signature;
    }
    
    public void recordOperation(long timestampNs, long durationNs, boolean isError) {
      // Record this in the summary histogram
      total.addSample(durationNs, isError);
      
      // And in the appropriate time bucket histogram.
      if (timeBucketNs > 0) {
        int bucketIndex = (int) ((timestampNs - startTimeNs) / timeBucketNs);
        while (timeBuckets.size() <= bucketIndex) {
          // We've run past the end of the bucket list; start a new bucket. First,
          // emit the previous bucket, if it's not empty.
          if (timeBuckets.size() > 0 && timeBuckets.get(timeBuckets.size() - 1).getCount() > 0) {
            logHistogram("Histogram " + bucketIndex + " for [" + signature + "]",
                timeBuckets.get(timeBuckets.size() - 1));
          }
        
          timeBuckets.add(new Histogram(MIN_HIST_VALUE_NS, MAX_HIST_VALUE_NS, HISTOGRAM_BUCKET_RATIO));
        }
      
        timeBuckets.get(bucketIndex).addSample(durationNs, isError);
      }
    }
  }
  
  /**
   * Print a human-readable summary of the given histogram.
   */
  private static void logHistogram(String label, Histogram hist) {
    System.out.println(label + ": "
        + hist.getCount() + " events (" + hist.getErrorCount() + " errors)"
        + ", mean " + describeNanos(hist.getMean())
        + ", 10th " + describeNanos(hist.percentile(0.10))
        + ", 50th " + describeNanos(hist.percentile(0.50))
        + ", 90th " + describeNanos(hist.percentile(0.90))
        + ", 99th " + describeNanos(hist.percentile(0.99))
        + ", 999th " + describeNanos(hist.percentile(0.999))
        + ", 9999th " + describeNanos(hist.percentile(0.9999))
        + ", min " + describeNanos(hist.getMinValue())
        + ", max " + describeNanos(hist.getMaxValue())
        );
  }
  
  /**
   * Given a duration measured in nanoseconds, return a compact string representation. 
   */
  private static String describeNanos(double ns) {
    if (ns < 1E6) {
      // Less than one millisecond. Emit as a fractional number of milliseconds, rounded to the
      // nearest microsecond.
      return Math.round(ns / 1E3) / 1E3 + "ms";
    } else if (ns < 1E7) {
      // Round to ten microseconds. We're going for a small number of significant digits, here.
      return Math.round(ns / 1E4) / 1E2 + "ms";
    } else if (ns < 1E8) {
      // Round to 100 microseconds.
      return Math.round(ns / 1E5) / 1E1 + "ms";
    } else if (ns < 1E9) {
      // Round to a millisecond.
      return Math.round(ns / 1E6) + "ms";
    } else if (ns < 1E10) {
      // Round to ten milliseconds.
      return Math.round(ns / 1E7) * 1E1 + "ms";
    } else if (ns < 1E11) {
      // Round to 100 milliseconds.
      return Math.round(ns / 1E8) * 1E2 + "ms";
    } else {
      // Round to one second.
      return Math.round(ns / 1E9) * 1E3 + "ms";
    }
  }
}
