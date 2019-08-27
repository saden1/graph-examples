package com.datastax.graphpractice.example;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.ContinuousPagingOptions;
import com.datastax.driver.dse.DseCluster;
import com.datastax.driver.dse.DseSession;
import com.datastax.driver.dse.graph.*;
import com.datastax.dse.graph.api.DseGraph;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import java.util.List;

public class App {
    private static DseSession session;
    private static GraphTraversalSource g;

    //This expects the contact point for the cluster as the first arg and the graph name as the second arg
    public static void main(String[] args) {
        setUp(args[0], args[1]);
        GraphTraversal t = g.V().limit(1000);

        //Test unpaged results
        unpagedResults(t);
        System.out.println("Finished Processing Unpaged Results");
        //Synchronous Paged results
        continuousPagingSynchronous(t);
        System.out.println("Finished Processing Synchronously Paged Results");

        //Test Asynchronous Paged results
        continuousPagingAsynchronous(t);
    }

    /**
     * This function demonstrates how to process unpaged graph results for DSE using the Gremlin Language Variant
     *
     * @param t The traversal to run
     */
    private static void unpagedResults(GraphTraversal t) {
        List<Vertex> vertices = t.toList();

        for (Vertex v : vertices) {
            // Process the row ...
        }
    }

    /**
     * This function demonstrates how to use continuous paging in synchronous traversals for graph results in DSE
     *
     * @param t The traversal to run
     */
    private static void continuousPagingSynchronous(GraphTraversal t) {
        GraphStatement statement = DseGraph.statementFromTraversal(t);

        //Currently you can only use the ContinuousPagingOptions.PageUnit of Rows not Bytes
        //Here is also where you set the page size for the vertices retrieved
        ContinuousPagingOptions options = ContinuousPagingOptions.builder().withPageSize(15, ContinuousPagingOptions.PageUnit.ROWS).build();
        statement.setPagingEnabled(true).setPagingOptions(options); //This enables continuous paging
        GraphResultSet rs = session.executeGraph(statement);

        for (GraphNode node : rs) {
            if (rs.getAvailableWithoutFetching() == 10 && !rs.isFullyFetched()) {
                rs.fetchMoreResults(); // this is asynchronous
            }
            // Process the row ...
        }
    }

    /**
     * This function demonstrates how to use continuous paging in asynchronous traversals for graph results in DSE
     *
     * @param t
     */
    private static void continuousPagingAsynchronous(GraphTraversal t) {
        GraphStatement statement = DseGraph.statementFromTraversal(t);

        //Currently you can only use the ContinuousPagingOptions.PageUnit of Rows not Bytes
        //Here is also where you set the page size for the vertices retrieved
        ContinuousPagingOptions options = ContinuousPagingOptions.builder().withPageSize(15, ContinuousPagingOptions.PageUnit.ROWS).build();
        statement.setPagingEnabled(true).setPagingOptions(options); //This enables continuous paging
       Futures.transform(
                session.executeGraphAsync(statement),
                processAsyncResults());
    }

    /**
     * This handles the callback for processing the asynchronous results returned from a graph traversal
     *
     * @return
     */
    private static AsyncFunction<GraphResultSet, GraphResultSet> processAsyncResults() {
        return new AsyncFunction<GraphResultSet, GraphResultSet>() {
            public ListenableFuture<GraphResultSet> apply(GraphResultSet rs){
                // How far we can go without triggering the blocking fetch:
                int remainingInPage = rs.getAvailableWithoutFetching();

                while (--remainingInPage >= 0) {
                    GraphNode node = rs.iterator().next();
                    // process the results here
                }
                if (rs.isFullyFetched()) {
                    System.out.println("Finished Processing Asynchronously Paged Results");
                    //Check to see if we have retrieved everything and if we have exit
                    return Futures.immediateFuture(rs);
                } else {
                    // If we have not then fetch the next set of results
                    ListenableFuture<GraphResultSet> future = rs.fetchMoreResults();
                    return Futures.transform(future, processAsyncResults());
                }
            }
        };
    }

    /**
     * Sets up the DseSession connection to the cluster
     * @param contactPoint  The contact point(s) for the cluster connection
     * @param graphName The name of the graph to connect to
     */
    public static void setUp(String contactPoint, String graphName) {
        DseCluster dseCluster = DseCluster.builder()
                .addContactPoint(contactPoint)
                .withGraphOptions(
                        new GraphOptions()
                                .setGraphName(graphName)
                                .setGraphSubProtocol(GraphProtocol.GRAPHSON_3_0)
                                .setGraphReadConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM)
                                .setGraphWriteConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM))
                .build();
        session = dseCluster.connect();
        g = DseGraph.traversal(session);
    }
}
