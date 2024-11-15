import java.io.*;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Vector;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.lib.input.*;
import org.apache.hadoop.mapreduce.lib.output.*;


class Vertex implements Writable {
    public long id;                   // the vertex ID
    public Vector<Long> adjacent;     // the vertex neighbors
    public long centroid;             // the id of the centroid in which this vertex belongs to
    public short depth;               // the BFS depth
    //public int adjsize;
    /* ... */

    public Vertex() {}

    public Vertex(long id, Vector<Long> adjacent,long centroid, short depth) {
        this.id = id;
        this.adjacent = adjacent;
        //this.adjsize = this.adjacent.size();
        this.centroid = centroid;
        this.depth = depth;
    }

    @Override
    public void write(DataOutput Do) throws IOException
    {
        Do.writeLong(id);
        Do.writeInt(adjacent.size());
        for(int j=0;j<adjacent.size();++j)
        {
            Do.writeLong(adjacent.get(j));
        }
        Do.writeLong(centroid);
        Do.writeShort(depth);

    }

    @Override
    public void readFields(DataInput DataIn) throws IOException
    {
        id = DataIn.readLong();
        int size = DataIn.readInt();
        Vector<Long> adj = new Vector<Long>();

        for(int i=0;i<size;++i)
        {
            adj.addElement(DataIn.readLong());
        }
        adjacent = adj;
        centroid = DataIn.readLong();
        depth = DataIn.readShort();
    }

    @Override
    public String toString(){ return id + "\t" + adjacent.toString() + "\t" + centroid + "\t" + depth;}

}

public class GraphPartition {
    final static short max_depth = 8;
    static short BFS_depth = 0;
    /* ... */

    public static class Map1 extends Mapper<Object,Text,LongWritable,Vertex> {
        static short tag = 1;
        @Override
        public void map ( Object key, Text value, Context context )
                throws IOException, InterruptedException {
            Scanner s = new Scanner(value.toString()).useDelimiter(",");
            long id = s.nextLong();
            Vector<Long> adjacent = new Vector<Long>();
            while(s.hasNext())
            {
                adjacent.add(s.nextLong());
            }
            if(tag <= 10)
            {
                context.write(new LongWritable(id), new Vertex(id, adjacent, id, (short)0));
                tag++;
            }
            else
                context.write(new LongWritable(id), new Vertex(id, adjacent, (long)-1, (short)0));
            s.close();
        }
    }

    public static class Map2 extends Mapper<LongWritable,Vertex,LongWritable,Vertex> {
        @Override
        public void map(LongWritable key, Vertex vertex, Context context) throws IOException, InterruptedException {
            // Write the current vertex to the context
            context.write(new LongWritable(vertex.id), vertex);

            // If the vertex has a positive centroid, add its adjacent vertices to the context with the same centroid and a BFS depth incremented by 1
            if (vertex.centroid > 0) {
                Iterator<Long> it = vertex.adjacent.iterator();
                while (it.hasNext()) {
                    Long k = it.next();
                    context.write(new LongWritable(k), new Vertex(k, new Vector<Long>(), vertex.centroid, (short) (BFS_depth + 1)));
                }
            }
        }

    }

    public static class Reduce2 extends Reducer<LongWritable, Vertex, LongWritable, Vertex> {
        @Override
        public void reduce(LongWritable id, Iterable<Vertex> vertices, Context context)
                throws IOException, InterruptedException {

            // Initialize the minimum depth to a large value
            short Depth_Min = Short.MAX_VALUE;
            Vertex outputVertex = new Vertex(id.get(), new Vector<Long>(), -1L, (short) 0);

            for (Vertex vertex : vertices) {
                // If the vertex has adjacent vertices, copy them to the output
                if (!vertex.adjacent.isEmpty()) {
                    outputVertex.adjacent = vertex.adjacent;
                }

                // If the vertex has a positive centroid and a depth smaller than the current minimum, update the minimum and copy the centroid
                if (vertex.centroid > 0 && vertex.depth < Depth_Min) {
                    Depth_Min = vertex.depth;
                    outputVertex.centroid = vertex.centroid;
                }
            }

            // Set the output vertex depth to the minimum depth found
            outputVertex.depth = Depth_Min;

            // Write the output vertex to the context
            context.write(id, outputVertex);
        }
    }


    public static class Map3 extends Mapper<LongWritable,Vertex,LongWritable,LongWritable> {
        @Override
        public void map ( LongWritable key, Vertex vertex, Context context )
                throws IOException, InterruptedException {
            // Scanner s = new Scanner(value.toString()).useDelimiter(",");
            context.write(new LongWritable(vertex.centroid), new LongWritable(1));
        }
    }

    public static class MyReducer3 extends Reducer<LongWritable, LongWritable, LongWritable, LongWritable> {
        @Override
        public void reduce(LongWritable centroid, Iterable<LongWritable> values, Context context)
                throws IOException, InterruptedException {

            // Compute the sum of all values associated with the centroid
            long sum = 0;
            for (LongWritable value : values) {
                sum += value.get();
            }

            // Write the centroid and its sum to the context
            context.write(centroid, new LongWritable(sum));
        }
    }


    public static void main ( String[] args ) throws Exception {
        Job Myjob = Job.getInstance();
        Myjob.setJobName("job1");
        Myjob.setJarByClass(GraphPartition.class);
        Myjob.setOutputKeyClass(LongWritable.class);
        Myjob.setOutputValueClass(Vertex.class);
        Myjob.setMapOutputKeyClass(LongWritable.class);
        Myjob.setMapOutputValueClass(Vertex.class);
        Myjob.setMapperClass(Map1.class);
        Myjob.setInputFormatClass(TextInputFormat.class);
        Myjob.setOutputFormatClass(SequenceFileOutputFormat.class);
        //Myjob.setOutputFormatClass(TextOutputFormat.class);
        FileInputFormat.setInputPaths(Myjob,new Path(args[0]));
        FileOutputFormat.setOutputPath(Myjob,new Path(args[1]+"/i0"));
        /* ... First Map-Reduce job to read the graph */
        Myjob.waitForCompletion(true);

        for ( short k = 0; k < max_depth; k++ ) {
            BFS_depth++;
            Job Myjob2 = Job.getInstance();
            Myjob2.setJobName("job2");
            Myjob2.setJarByClass(GraphPartition.class);
            Myjob2.setOutputKeyClass(LongWritable.class);
            Myjob2.setOutputValueClass(Vertex.class);
            Myjob2.setMapOutputKeyClass(LongWritable.class);
            Myjob2.setMapOutputValueClass(Vertex.class);
            Myjob2.setMapperClass(Map2.class);
            Myjob2.setReducerClass(Reduce2.class);
            Myjob2.setInputFormatClass(SequenceFileInputFormat.class);
            Myjob2.setOutputFormatClass(SequenceFileOutputFormat.class);
            //Myjob2.setOutputFormatClass(TextOutputFormat.class);
            FileInputFormat.setInputPaths(Myjob2,new Path(args[1]+"/i"+k));
            FileOutputFormat.setOutputPath(Myjob2,new Path(args[1]+"/i"+(k+1)));
            /* ... Second Map-Reduce job to do BFS */
            Myjob2.waitForCompletion(true);
        }

        Job Myjob3 = Job.getInstance();
        Myjob3.setJobName("MyMyjob3");
        Myjob3.setJarByClass(GraphPartition.class);
        Myjob3.setOutputKeyClass(LongWritable.class);
        Myjob3.setOutputValueClass(LongWritable.class);
        Myjob3.setMapOutputKeyClass(LongWritable.class);
        Myjob3.setMapOutputValueClass(LongWritable.class);
        Myjob3.setMapperClass(Map3.class);
        Myjob3.setReducerClass(MyReducer3.class);
        Myjob3.setInputFormatClass(SequenceFileInputFormat.class);
        //Myjob3.setOutputFormatClass(SequenceFileOutputFormat.class);
        Myjob3.setOutputFormatClass(TextOutputFormat.class);
        FileInputFormat.setInputPaths(Myjob3,new Path(args[1]+"/i8"));
        FileOutputFormat.setOutputPath(Myjob3,new Path(args[2]));
        /* ... Final Map-Reduce job to calculate the cluster sizes */
        Myjob3.waitForCompletion(true);
    }
}
