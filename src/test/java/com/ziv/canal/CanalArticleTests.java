package com.ziv.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.ziv.search.ArticleSearchService;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;

class CanalArticleTests {
    @Test
    void updateDeduplicatesIdsAndDeleteReadsBeforeColumns() throws Exception {
        var search = mock(ArticleSearchService.class);
        var processor = new ArticleChangeProcessor();
        ReflectionTestUtils.setField(processor, "search", search);
        ReflectionTestUtils.setField(processor, "schema", "blogweb");
        processor.process(new Message(1L, List.of(entry(CanalEntry.EventType.UPDATE, "1"),
                entry(CanalEntry.EventType.UPDATE, "1"), entry(CanalEntry.EventType.DELETE, "2"))));
        verify(search).synchronize(1L);
        verify(search).synchronize(2L);
        verifyNoMoreInteractions(search);
    }

    @Test
    void acknowledgementOccursOnlyAfterProcessingSucceeds() throws Exception {
        var connector = mock(CanalConnector.class);
        var processor = mock(ArticleChangeProcessor.class);
        var consumer = consumer(connector, processor);
        var message = new Message(10L, List.of());
        when(connector.getWithoutAck(100, 1L, TimeUnit.SECONDS)).thenReturn(message);
        consumer.poll();
        var order = inOrder(connector, processor);
        order.verify(processor).process(message);
        order.verify(connector).ack(10L);
        consumer.stop();
    }

    @Test
    void failedBatchIsNotAcknowledgedAndIsRetriedAfterReconnect() throws Exception {
        var connector = mock(CanalConnector.class);
        var processor = mock(ArticleChangeProcessor.class);
        var consumer = consumer(connector, processor);
        var message = new Message(10L, List.of());
        when(connector.getWithoutAck(100, 1L, TimeUnit.SECONDS)).thenReturn(message);
        doThrow(new IllegalStateException("Elasticsearch unavailable")).doNothing().when(processor).process(message);
        consumer.poll();
        verify(connector, never()).ack(anyLong());
        verify(connector, times(2)).rollback();
        verify(connector).disconnect();
        consumer.poll();
        verify(connector, times(2)).connect();
        verify(connector).ack(10L);
        consumer.stop();
    }

    private CanalArticleConsumer consumer(CanalConnector connector, ArticleChangeProcessor processor) {
        var consumer = new CanalArticleConsumer();
        ReflectionTestUtils.setField(consumer, "connector", connector);
        ReflectionTestUtils.setField(consumer, "processor", processor);
        ReflectionTestUtils.setField(consumer, "schema", "blogweb");
        ReflectionTestUtils.setField(consumer, "destination", "example");
        ReflectionTestUtils.setField(consumer, "batchSize", 100);
        return consumer;
    }

    private CanalEntry.Entry entry(CanalEntry.EventType type, String id) {
        var column = CanalEntry.Column.newBuilder().setName("id").setValue(id).build();
        var row = CanalEntry.RowData.newBuilder();
        if (type != CanalEntry.EventType.INSERT) row.addBeforeColumns(column);
        if (type != CanalEntry.EventType.DELETE) row.addAfterColumns(column);
        var change = CanalEntry.RowChange.newBuilder().setEventType(type).addRowDatas(row).build();
        return CanalEntry.Entry.newBuilder().setEntryType(CanalEntry.EntryType.ROWDATA)
                .setHeader(CanalEntry.Header.newBuilder().setSchemaName("blogweb").setTableName("articles"))
                .setStoreValue(change.toByteString()).build();
    }
}
