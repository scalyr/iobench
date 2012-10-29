/**
 * Class for aggregating numeric values into a histogram.
 */
public class Histogram {
  /**
   * The range of values supported by this histogram. All values are pinned to this
   * range before inserting into the histogram.
   */
  private final double pinMinimum, pinMaximum;

  /**
   * Ratio of values in a bucket, e.g. 1.1 means that the first bucket counts
   * values in the range [pinMinimum, pinMinimum * 1.1).
   */
  private final double bucketRatio;

  /**
   * Math.log(bucketRatio)
   */
  private final double logBucketRatio;

  /**
   * Number of samples in each histogram bucket.
   */
  private long[] counts;

  /**
   * Sum of all sample values, *before* pinning to pinMinimum / pinMaximum. Can be divided by
   * totalCount to yield the mean sample value.
   */
  private double totalValue;

  /**
   * Total number of samples (equal to the sum of the counts array).
   */
  private long count;

  /**
   * Number of samples which reflected an error outcome. (A subset of totalCount.)
   */
  private long errorCount;

  /**
   * Minimum and maximum sample values, *before* pinning to pinMinimum / pinMaximum. If
   * no samples have yet been provided, these fields hold 0.
   */
  private double minValue, maxValue;

  /**
   * Construct a new, empty histogram. Values inserted into the histogram will
   * be pinned to [pinMinimum, pinMaximum] and then bucketed into logarithmic buckets with
   * ratio bucketRatio. E.g. if pinMinimum is 1, pinMaximum is 10, and bucketRatio is 2,
   * we will have buckets [1, 2), [2, 4), [4, 8), and [8, 16).
   */
  public Histogram(double pinMinimum, double pinMaximum, double bucketRatio) {
    assert(pinMinimum > 0); // otherwise our ratio computations will break down

    this.pinMinimum = pinMinimum;
    this.pinMaximum = pinMaximum;
    this.bucketRatio = bucketRatio;
    this.logBucketRatio = Math.log(bucketRatio);

    counts = new long[(int) Math.ceil(Math.log(pinMaximum / pinMinimum) / logBucketRatio + 1E-9)];
  }

  /**
   * Return a JSON representation of this histogram.
   */
  public JsonObject toJson() {
    int firstNonzeroBucket = 0;
    while (firstNonzeroBucket < counts.length && counts[firstNonzeroBucket] == 0)
      firstNonzeroBucket++;
    
    int lastNonzeroBucket = counts.length - 1;
    while (lastNonzeroBucket >= firstNonzeroBucket && counts[lastNonzeroBucket] == 0)
      lastNonzeroBucket--;
    
    JsonArray countsJson = new JsonArray();
    for (int i = firstNonzeroBucket; i <= lastNonzeroBucket; i++)
      countsJson.add(counts[i]);
    
    return new JsonObject()
        .set("pinMinimum", pinMinimum)
        .set("pinMaximum", pinMaximum)
        .set("bucketRatio", bucketRatio)
        .set("firstBucketStart", getBucketMin(firstNonzeroBucket))
        .set("totalValue", totalValue)
        .set("count", count)
        .set("errorCount", errorCount)
        .set("minValue", minValue)
        .set("maxValue", maxValue)
        .set("buckets", countsJson)
        ;
  }
  
  /**
   * Return the smallest value that belongs in the bucket associated with
   * counts[bucketIndex].
   */
  public double getBucketMin(int bucketIndex) {
    assert(bucketIndex >= 0 && bucketIndex <= counts.length);
    return pinMinimum * Math.exp(logBucketRatio * bucketIndex);
  }

  /**
   * Return the largest value that belongs in the bucket associated with
   * counts[bucketIndex].
   */
  public double getBucketMax(int bucketIndex) {
    return getBucketMin(bucketIndex) * bucketRatio;
  }

  /**
   * Add the given value to the histogram.
   * 
   * @param value The numeric value to insert.
   * @param isError True if this value is associated with an "error" outcome (causes
   *     us to increment errorCount).
   */
  public void addSample(double value, boolean isError) {
    if (count == 0) {
      minValue = value;
      maxValue = value;
    } else {
      minValue = Math.min(minValue, value);
      maxValue = Math.max(maxValue, value);
    }

    count += 1;
    totalValue += value;
    if (isError)
      errorCount++;

    double pinnedValue = Math.max(Math.min(value, pinMaximum), pinMinimum);

    double x = Math.log(pinnedValue / pinMinimum) / logBucketRatio;
    int bucketIndex = (int) Math.floor(x);
    counts[bucketIndex]++;
    
  }

  /**
   * Return the mean of all values inserted into this histogram, or 0 if the histogram is empty.
   */
  public double getMean() {
    return (count > 0) ? totalValue / count : 0;
  }

  /**
   * Return the number of values inserted into this histogram.
   */
  public double getCount() {
    return count;
  }

  /**
   * Return the number of values inserted into this histogram for which isError was true.
   */
  public double getErrorCount() {
    return errorCount;
  }

  public double getMinValue() {
    return minValue;
  }

  public double getMaxValue() {
    return maxValue;
  }
  
  /**
   * Return an estimate of the specified percentile value, e.g. if p = 0.5 we
   * return the median. If the histogram is empty, we return 0.
   */
  public double percentile(double p) {
    assert(p > 0 && p < 1);

    if (count == 0)
      return 0;

    // Find the first bucket such that the cumulative count total reaches p.
    double neededTotal = count * p;
    int bucketIndex = 0;
    double totalSoFar = counts[0];
    while (totalSoFar == 0 || totalSoFar + 1E-6 < neededTotal) {
      bucketIndex++;
      totalSoFar += counts[bucketIndex];
    }

    // Interpolate within the range of values for this bucket.
    double inBucket = counts[bucketIndex];
    double numAbove = totalSoFar - neededTotal;
    double numBelow = inBucket - numAbove;

    double result = pinMinimum * Math.pow(bucketRatio, bucketIndex + numBelow / (double) inBucket);
    return Math.max(Math.min(result, maxValue), minValue);
  }
}
