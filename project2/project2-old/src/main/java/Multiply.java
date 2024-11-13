import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.*;
import org.apache.hadoop.mapreduce.lib.output.*;

class Triple implements Writable {
    public int tag;
    public int index;
    public float value;

    public Triple()
    {}
    public Triple(int nextShort, int nextInt, float nextFloat) {
        this.tag = nextShort;
        this.index = nextInt;
        this.value =  nextFloat;

    }

    @Override
    public void write(DataOutput Do) throws IOException {
        Do.writeInt(this.tag);
        Do.writeInt(this.index);
        Do.writeFloat(this.value);

    }

    @Override
    public void readFields(DataInput Di) throws IOException {
        this.tag = Di.readInt();
        this.index = Di.readInt();
        this.value =  Di.readFloat();
    }
    /* write your code here */
}

class Pair implements WritableComparable<Pair> {
    public int i;
    public int j;
    public Pair()
    {}

    public Pair(int i, int j) {
        this.i = i;
        this.j = j;
    }

    @Override
    public String toString() { return String.format("(%d,%d)",i,j); }

    @Override
    public int compareTo(Pair o) {
        if (i == o.i && j == o.j) {
            return 0;
        } else if (i < o.i || (i == o.i && j < o.j)) {
            return -1;
        } else {
            return 1;
        }
    }

    @Override
    public void write(DataOutput Do) throws IOException {
        Do.writeInt(i);
        Do.writeInt(j);
    }

    @Override
    public void readFields(DataInput Di) throws IOException {
        i = Di.readInt();
        j = Di.readInt();
    }

    /* write your code here */
}
public class Multiply {
    public static class Map_MatrixM extends Mapper<Object,Text,IntWritable,Triple>
    {
        @Override
        public void map (Object key, Text value, Context context )
                throws IOException, InterruptedException {
            Scanner S1 = new Scanner(value.toString()).useDelimiter(",");
            int i = S1.nextShort();
            int k = S1.nextInt();
            float m = S1.nextFloat();
            context.write(new IntWritable(k),new Triple(0,i,m));

        }
    }
    public static class Map_MatrixN extends Mapper<Object,Text,IntWritable,Triple>
    {
        @Override
        public void map ( Object key, Text value, Context context )
                throws IOException, InterruptedException {
            Scanner S2 = new Scanner(value.toString()).useDelimiter(",");
            int k = S2.nextShort();
            int j = S2.nextInt();
            float n = S2.nextFloat();
            context.write(new IntWritable(k),new Triple( 1,j,n));

        }
    }
    public static class first_Reducer extends Reducer<IntWritable,Triple,Pair,FloatWritable> {
        ArrayList<Triple> M_arraylist = new ArrayList<>();
        ArrayList<Triple> N_arraylist = new ArrayList<>();
        @Override
        public void reduce (IntWritable key, Iterable<Triple> values, Context context )
                throws IOException, InterruptedException {
            M_arraylist.clear();
            N_arraylist.clear();
            Iterator<Triple> iterator = values.iterator();
            while (iterator.hasNext()) {
                Triple trip = iterator.next();
                if (trip.tag == 0) {
                    Triple newtrip = new Triple(trip.tag, trip.index, trip.value);
                    M_arraylist.add(newtrip);
                } else {
                    Triple newtrip = new Triple(trip.tag, trip.index, trip.value);
                    N_arraylist.add(newtrip);
                }
            }
            int Mat_M = 0;
            while (Mat_M < M_arraylist.size()) {
                int Mat_N = 0;
                while (Mat_N < N_arraylist.size()) {
                    int mIndex = M_arraylist.get(Mat_M).index;
                    int nIndex = N_arraylist.get(Mat_N).index;
                    float mValue = M_arraylist.get(Mat_M).value;
                    float nValue = N_arraylist.get(Mat_N).value;
                    Pair pair = new Pair(mIndex, nIndex);
                    FloatWritable product = new FloatWritable(mValue * nValue);
                    context.write(pair, product);
                    Mat_N++;
                }
                Mat_M++;
            }

        }}
    public static class second_Mapper extends Mapper<Object, Text, Pair, FloatWritable> {
        @Override
        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            Pattern pattern = Pattern.compile("[(),\\s]+");
            Matcher matcher = pattern.matcher(value.toString());
            List<String> partsList = new ArrayList<>();
            int index = 0;
            while (matcher.find()) {
                partsList.add(value.toString().substring(index, matcher.start()));
                index = matcher.end();
            }
            if (index < value.toString().length()) {
                partsList.add(value.toString().substring(index));
            }
            String[] pieces = partsList.toArray(new String[0]);

            int p1 = Integer.parseInt(pieces[1]);
            int p2 = Integer.parseInt(pieces[2]);
            float val = Float.parseFloat(pieces[3]);
            context.write(new Pair(p1, p2), new FloatWritable(val));
        }
    }

    public static class second_Reducer extends Reducer<Pair, FloatWritable, Pair, FloatWritable> {
        @Override
        public void reduce(Pair key, Iterable<FloatWritable> values, Context context) throws IOException, InterruptedException {
            float pair_Summation = 0;
            for (FloatWritable summation : values) {
                pair_Summation += summation.get();
            }
            context.write(key, new FloatWritable(pair_Summation));

        }
    }

    public int run(String[] args) throws Exception {
        return 0;
    }


    /* write your code here */

    public static void main ( String[] args ) throws Exception {
        /* write your code here */
        Job Mul_Job = Job.getInstance();
        Mul_Job.setJobName("Multiplication of Two Matrices");
        Mul_Job.setJarByClass(Multiply.class);
        Mul_Job.setOutputKeyClass(Pair.class);
        Mul_Job.setOutputValueClass(FloatWritable.class);
        Mul_Job.setMapOutputKeyClass(IntWritable.class);
        Mul_Job.setMapOutputValueClass(Triple.class);
        Mul_Job.setReducerClass(first_Reducer.class);
        Mul_Job.setOutputFormatClass(TextOutputFormat.class);
        MultipleInputs.addInputPath(Mul_Job,new Path(args[0]),TextInputFormat.class,Map_MatrixM.class);
        MultipleInputs.addInputPath(Mul_Job,new Path(args[1]),TextInputFormat.class,Map_MatrixN.class);
        FileOutputFormat.setOutputPath(Mul_Job,new Path(args[2]));
        Mul_Job.waitForCompletion(true);

        Job Add_Job = Job.getInstance();


        Add_Job.setJarByClass(Multiply.class);
        Add_Job.setJobName("Multiplication of Two Matrices- II");

        Add_Job.setOutputFormatClass(TextOutputFormat.class);
        Add_Job.setMapOutputKeyClass(Pair.class);
        Add_Job.setMapOutputValueClass(FloatWritable.class);
        Add_Job.setOutputKeyClass(Pair.class);
        Add_Job.setOutputValueClass(TextOutputFormat.class);


        Add_Job.setMapperClass(second_Mapper.class);
        Add_Job.setReducerClass(second_Reducer.class);
        Add_Job.setInputFormatClass(TextInputFormat.class);


        FileInputFormat.addInputPath(Add_Job, new Path(args[2]));
        FileOutputFormat.setOutputPath(Add_Job, new Path(args[3]));

        Add_Job.waitForCompletion(true);
    }
}