import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class Twitter {

    private static int[] parseUserData(String line) {
        try {
            String[] userData = line.split(",");
            if (userData.length == 2) {
                return new int[]{Integer.parseInt(userData[0]), Integer.parseInt(userData[1])};
            }
        } catch (NumberFormatException e) {
            System.err.println("Error parsing user data: " + e.getMessage());
        }
        return null;
    }

    public static class FollowerMapper extends Mapper<Object, Text, IntWritable, IntWritable> {
        private final IntWritable follower = new IntWritable();
        private final IntWritable user = new IntWritable();

        @Override
        protected void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            try {
                int[] parsedData = parseUserData(value.toString());
                if (parsedData != null) {
                    setUserAndFollower(parsedData);
                    context.write(follower, user);  // Writing follower_id -> user_id to context
                }
            } catch (Exception e) {
                System.err.println("Error in FollowerMapper: " + e.getMessage());
            }
        }

        private void setUserAndFollower(int[] data) {
            user.set(data[0]);
            follower.set(data[1]);
        }
    }

    public static class FollowerCountReducer extends Reducer<IntWritable, IntWritable, IntWritable, IntWritable> {
        private final IntWritable count = new IntWritable();

        @Override
        protected void reduce(IntWritable followerId, Iterable<IntWritable> followedUsers, Context context) throws IOException, InterruptedException {
            try {
                int followCount = countFollowedUsers(followedUsers);
                count.set(followCount);
                context.write(followerId, count);  // Emit follower_id -> count (how many people they follow)
            } catch (Exception e) {
                System.err.println("Error in FollowerCountReducer: " + e.getMessage());
            }
        }

        private int countFollowedUsers(Iterable<IntWritable> followedUsers) {
            int followCount = 0;
            for (IntWritable user : followedUsers) {
                followCount++;
            }
            return followCount;
        }
    }

    public static class FollowGroupMapper extends Mapper<Object, Text, IntWritable, IntWritable> {
        private final IntWritable followCount = new IntWritable();
        private final IntWritable one = new IntWritable(1);

        @Override
        protected void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            try {
                int followCountValue = extractFollowCount(value.toString());
                if (followCountValue != -1) {
                    followCount.set(followCountValue);
                    context.write(followCount, one);  // Write follow_count -> 1 (we're counting them)
                }
            } catch (Exception e) {
                System.err.println("Error in FollowGroupMapper: " + e.getMessage());
            }
        }

        private int extractFollowCount(String line) {
            try {
                String[] userFollowData = line.split("\t");
                if (userFollowData.length == 2) {
                    return Integer.parseInt(userFollowData[1]);
                }
            } catch (NumberFormatException e) {
                System.err.println("Error extracting follow count: " + e.getMessage());
            }
            return -1;
        }
    }

    public static class FollowGroupReducer extends Reducer<IntWritable, IntWritable, IntWritable, IntWritable> {
        private final IntWritable groupCount = new IntWritable();

        @Override
        protected void reduce(IntWritable followCount, Iterable<IntWritable> counts, Context context) throws IOException, InterruptedException {
            try {
                int total = sumFollowGroup(counts);
                groupCount.set(total);
                context.write(followCount, groupCount);  // Output follow_count -> total users in this group
            } catch (Exception e) {
                System.err.println("Error in FollowGroupReducer: " + e.getMessage());
            }
        }

        private int sumFollowGroup(Iterable<IntWritable> counts) {
            int total = 0;
            for (IntWritable count : counts) {
                total += count.get();
            }
            return total;
        }
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: Twitter <input path> <temp output path> <final output path>");
            System.exit(-1);
        }

        try {
            Configuration conf = new Configuration();

            Job followerCountJob = setupJob(conf, "Follower Count", Twitter.class, FollowerMapper.class, FollowerCountReducer.class, args[0], args[1]);
            if (!followerCountJob.waitForCompletion(true)) {
                System.exit(1);
            }

            Job followGroupJob = setupJob(conf, "Follow Grouping", Twitter.class, FollowGroupMapper.class, FollowGroupReducer.class, args[1], args[2]);
            System.exit(followGroupJob.waitForCompletion(true) ? 0 : 1);

        } catch (IOException | ClassNotFoundException | InterruptedException e) {
            System.err.println("Error in main: " + e.getMessage());
        }
    }

    private static Job setupJob(Configuration conf, String jobName, Class<?> jarClass, Class<? extends Mapper> mapperClass, Class<? extends Reducer> reducerClass, String inputPath, String outputPath) throws IOException {
        Job job = null;
        try {
            job = Job.getInstance(conf, jobName);
            job.setJarByClass(jarClass);
            job.setMapperClass(mapperClass);
            job.setReducerClass(reducerClass);
            job.setOutputKeyClass(IntWritable.class);
            job.setOutputValueClass(IntWritable.class);
            FileInputFormat.addInputPath(job, new Path(inputPath));
            FileOutputFormat.setOutputPath(job, new Path(outputPath));
        } catch (IOException e) {
            System.err.println("Error setting up job: " + e.getMessage());
        }
        return job;
    }
}
