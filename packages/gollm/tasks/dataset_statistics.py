import numpy as np
import pandas as pd
import sys
import traceback

from entities import DatasetStatistics
from taskrunner import TaskRunnerInterface

def convert_numpy_types(obj):
    """
    Recursively convert NumPy types to standard Python types.
    """
    if isinstance(obj, np.integer):
        return int(obj)
    elif isinstance(obj, np.floating):
        return float(obj)
    elif isinstance(obj, np.ndarray):
        return obj.tolist()
    elif isinstance(obj, dict):
        return {k: convert_numpy_types(v) for k, v in obj.items()}
    elif isinstance(obj, list):
        return [convert_numpy_types(v) for v in obj]
    return obj

def cleanup():
    pass

def main():
    global taskrunner
    exit_code = 0

    try:
        taskrunner = TaskRunnerInterface(description="Dataset Statistics CLI")
        taskrunner.on_cancellation(cleanup)

        input_dict = taskrunner.read_input_dict_with_timeout()
        csv_url = DatasetStatistics(**input_dict).datasetUrl
        taskrunner.log(f"Reading CSV from {csv_url}")

        taskrunner.log("Creating statistics from input")

        # Read CSV from string
        df = pd.read_csv(csv_url, low_memory=False)

        taskrunner.log(f"Read {len(df)} rows and {len(df.columns)} columns")

        # Prepare a dictionary to store results
        stats_summary = {}

        # Iterate through numeric columns
        for column in df.select_dtypes(include=[np.number]).columns:
            # Calculate basic statistics
            column_stats = {
                'data_type': str(df[column].dtype),
                'mean': df[column].mean(),
                'median': df[column].median(),
                'min': df[column].min(),
                'max': df[column].max(),
                'std_dev': df[column].std(),
                'quartiles': df[column].quantile([0.25, 0.5, 0.75]).tolist(),
                'unique_values': df[column].nunique(),
                'missing_values': df[column].isnull().sum(),
            }

            # Add distribution binning
            # If there are fewer than 10 unique values, use that number of bins, otherwise use 10
            n_bins = min(10, df[column].nunique()) if df[column].nunique() > 1 else 2
            # Calculate histogram bins
            values, bin_edges = np.histogram(df[column].dropna(), bins=n_bins)
            column_stats['histogram_bins'] = [
                {
                    "start": f"{bin_edges[i]:.2f}",
                    "end": f"{bin_edges[i+1]:.2f}",
                    "count": int(values[i])
                }
                for i in range(len(values))
            ]
            taskrunner.log(column_stats['histogram_bins'])


            # Add to summary
            stats_summary[column] = column_stats

        # Non-numeric column analysis
        non_numeric_columns = df.select_dtypes(exclude=[np.number]).columns
        non_numeric_summary = {}
        for column in non_numeric_columns:
            non_numeric_summary[column] = {
                'data_type': str(df[column].dtype),
                'unique_values': df[column].nunique(),
                'most_common': df[column].value_counts().head(5).to_dict(),
                'missing_values': df[column].isnull().sum()
            }

        response = convert_numpy_types({
            'numeric_columns': stats_summary,
            'non_numeric_columns': non_numeric_summary,
            'total_rows': len(df),
            'total_columns': len(df.columns)
        })

        taskrunner.log("Statistics from input created")
        taskrunner.write_output_dict_with_timeout(response)

    except Exception as e:
        sys.stderr.write(traceback.format_exc())
        sys.stderr.flush()
        exit_code = 1

    taskrunner.log("Shutting down")
    taskrunner.shutdown()
    sys.exit(exit_code)

if __name__ == "__main__":
    main()
