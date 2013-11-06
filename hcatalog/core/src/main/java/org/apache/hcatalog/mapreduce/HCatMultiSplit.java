package org.apache.hcatalog.mapreduce;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.hadoop.io.WritableUtils;

public class HCatMultiSplit extends HCatSplit {

  /** The table name of HCatTable, format: [dbn name].[table name] */
  private String tableName;
  /** The table Id used to identify duplicate tables */
  private int tableIndex;

  public HCatMultiSplit() {
  }

  public HCatMultiSplit(HCatSplit split, String tableName, int tableIndex) {
    super(split.getPartitionInfo(), split.getBaseSplit(), split.getTableSchema());
    this.tableName = tableName;
    this.tableIndex = tableIndex;
  }

  public String getTableName() {
    return tableName;
  }

  public int getTableIndex() {
    return tableIndex;
  }

  @Override
  public void readFields(DataInput input) throws IOException {
    tableName = WritableUtils.readString(input);
    tableIndex = WritableUtils.readVInt(input);
    super.readFields(input);
  }

  @Override
  public void write(DataOutput output) throws IOException {
    WritableUtils.writeString(output, tableName);
    WritableUtils.writeVInt(output, tableIndex);
    super.write(output);
  }
}
