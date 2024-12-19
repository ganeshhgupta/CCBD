-- Load the graph from the given path '$G'. Each line is an edge represented by (source, destination).
graph_data = LOAD '$G' USING PigStorage(',') AS (source: int, destination: int);

-- Group by source node and count the number of neighbors (outgoing edges) each node has.
node_neighbors = GROUP graph_data BY source;
neighbor_counts = FOREACH node_neighbors GENERATE group AS node, COUNT(graph_data.destination) AS neighbor_count;

-- Group nodes by the neighbor count value to find how many nodes have the same count.
grouped_by_neighbor_count = GROUP neighbor_counts BY neighbor_count;

-- Count the number of nodes in each group, giving the final result as (neighbor_count, node_count).
result = FOREACH grouped_by_neighbor_count GENERATE group AS neighbor_count, COUNT(neighbor_counts) AS node_count;

-- Store the result or dump it for verification
DUMP result;
