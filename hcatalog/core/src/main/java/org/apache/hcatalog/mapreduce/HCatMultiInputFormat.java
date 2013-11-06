package org.apache.hcatalog.mapreduce;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hcatalog.common.HCatConstants;
import org.apache.hcatalog.common.HCatUtil;
import org.apache.hcatalog.data.HCatRecord;
import org.apache.hcatalog.data.schema.HCatFieldSchema;
import org.apache.hcatalog.data.schema.HCatSchema;

public class HCatMultiInputFormat extends HCatBaseInputFormat {

  public static void setInput(Job job, ArrayList<InputJobInfo> inputJobInfoList)
                        throws IOException {
    try {
      InitializeInput.setInput(job, inputJobInfoList);
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  public static HCatSchema getTableSchema(JobContext context, String tableName)
                        throws IOException {
    List<InputJobInfo> list = getJobInfoList(context);
    InputJobInfo inputJobInfo = null;
    for (InputJobInfo jobInfo : list) {
      if (tableName.equals(generateTableName(jobInfo))) {
        inputJobInfo = jobInfo;
        break;
      }
    }
    if (inputJobInfo == null) {
      throw new IOException("no job information found for table " + tableName);
    }

    HCatSchema allCols = new HCatSchema(new LinkedList<HCatFieldSchema>());
    for (HCatFieldSchema field : inputJobInfo.getTableInfo().getDataColumns().getFields()) {
      allCols.append(field);
    }
    for (HCatFieldSchema field : inputJobInfo.getTableInfo().getPartitionColumns().getFields()) {
      allCols.append(field);
    }
    return allCols;
  }

  public static HCatSchema getTableSchema(JobContext context)
                        throws IOException {
    throw new IOException(
        "getTableSchema(JobContext context) is not supported in HCatMultiInputFormat");
  }

  public static HCatSchema getOutputSchema(JobContext context, String tableName)
                        throws IOException {
    String os = context.getConfiguration().get(
                                HCatConstants.HCAT_KEY_OUTPUT_SCHEMA);
    if (os == null) {
      return getTableSchema(context, tableName);
    } else {
      return (HCatSchema) HCatUtil.deserialize(os);
    }
  }

  private static List<InputJobInfo> getJobInfoList(JobContext jobContext)
                        throws IOException {
    String jobListString = jobContext.getConfiguration().get(
                                HCatConstants.HCAT_KEY_MULTI_INPUT_JOBS_INFO);
    if (jobListString == null) {
      throw new IOException("job information list not found in JobContext."
                                        + " HCatMultiInputFormat.setInput() not called?");
    }

    ArrayList<InputJobInfo> list = (ArrayList<InputJobInfo>) HCatUtil.deserialize(jobListString);
    if (list == null) {
      throw new IOException("job information Map is null or is empty. "
                                        + " HCatMultiInputFormat.setInput() not called correctly?");
    }
    return list;
  }

  private static String generateTableName(InputJobInfo jobInfo) {
    return jobInfo.getDatabaseName() + "." + jobInfo.getTableName();
  }

  private static HCatMultiSplit castToHMultiCatSplit(InputSplit split) throws IOException{
    if (split instanceof HCatMultiSplit) {
      return (HCatMultiSplit) split;
    } else {
      throw new IOException("Split must be " + HCatMultiSplit.class.getName()
          + " but found " + split.getClass().getName());
    }
  }

  @Override
  public RecordReader<WritableComparable, HCatRecord>
        createRecordReader(InputSplit split,
                        TaskAttemptContext taskContext) throws IOException, InterruptedException {

    HCatMultiSplit hcatSplit = castToHMultiCatSplit(split);

    PartInfo partitionInfo = hcatSplit.getPartitionInfo();
    JobContext jobContext = taskContext;

    HCatStorageHandler storageHandler = HCatUtil.getStorageHandler(
                                jobContext.getConfiguration(), partitionInfo);

    JobConf jobConf = HCatUtil.getJobConfFromContext(jobContext);
    Map<String, String> jobProperties = partitionInfo.getJobProperties();
    HCatUtil.copyJobPropertiesToJobConf(jobProperties, jobConf);

    Map<String, String> valuesNotInDataCols = getColValsNotInDataColumns(
                                getOutputSchema(jobContext, hcatSplit.getTableName()),
        partitionInfo);

    return new HCatRecordReader(storageHandler, valuesNotInDataCols);
  }

  @Override
  public List<InputSplit> getSplits(JobContext jobContext)
                        throws IOException, InterruptedException {
    List<InputJobInfo> inputJobInfoList = getJobInfoList(jobContext);
    List<InputSplit> splits = new ArrayList<InputSplit>();
    int tableIndex = 1;
    for (InputJobInfo inputJobInfo : inputJobInfoList) {
      List<InputSplit> oneTableSplits = getSplits(jobContext, inputJobInfo);
      String tableName = generateTableName(inputJobInfo);
      for (InputSplit split : oneTableSplits) {
        HCatSplit hCatSplit = (HCatSplit) split;
        HCatMultiSplit newMultiSplit = new HCatMultiSplit(hCatSplit, tableName, tableIndex);
        splits.add(newMultiSplit);
      }
      tableIndex++;
    }
    return splits;
  }
}
