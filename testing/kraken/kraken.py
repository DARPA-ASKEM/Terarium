import multiprocessing
import subprocess
import os
import sys
import time
import signal


def parse_arguments2(arguments):
    parsed_args = []
    for arg in arguments:
        parsed_args.append(arg.split(":"))
    return parsed_args

def run_subscript(script_name, iter):
    sub_env = os.environ.copy()
    sub_env["ITERATION"] = iter
    process = subprocess.Popen(['python', f"profiles/{script_name}.py"], env=sub_env)
    return process


def run():
    # Tracker
    process_list = []

    # Collect run manifest
    args = sys.argv[1:]
    parsed_args = parse_arguments2(args)

    for config in parsed_args:
        script = config[0]
        instances = int(config[1])
        iter = 1
        if len(config) == 3:
            iter = config[2]

        for _ in range(instances):
            sub_process = run_subscript(script, iter)
            process_list.append(sub_process)

    for p in process_list:
        p.wait()

    print("All done!!!")



if __name__ == "__main__":
    run()

