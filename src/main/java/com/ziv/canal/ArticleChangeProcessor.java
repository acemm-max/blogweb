package com.ziv.canal;

import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.ziv.search.ArticleSearchService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ArticleChangeProcessor {
    @Value("${blogweb.canal.schema}")
    private String schema;
    @Autowired private ArticleSearchService search;

    public void process(Message message) throws com.google.protobuf.InvalidProtocolBufferException {
        Set<Long> ids = new LinkedHashSet<>();
        for (CanalEntry.Entry entry : message.getEntries()) {
            if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA
                    || !schema.equals(entry.getHeader().getSchemaName())
                    || !"articles".equals(entry.getHeader().getTableName())) continue;
            CanalEntry.RowChange change = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
            if (change.getIsDdl()) {
                if (change.getEventType() == CanalEntry.EventType.TRUNCATE) {
                    throw new IllegalStateException("Article TRUNCATE requires an index rebuild before resuming Canal");
                }
                continue;
            }
            if (change.getEventType() != CanalEntry.EventType.INSERT
                    && change.getEventType() != CanalEntry.EventType.UPDATE
                    && change.getEventType() != CanalEntry.EventType.DELETE) continue;
            for (CanalEntry.RowData row : change.getRowDatasList()) {
                // Both sides also handle primary-key changes and DELETE events.
                collect(ids, row.getBeforeColumnsList());
                collect(ids, row.getAfterColumnsList());
            }
        }
        for (Long id : ids) search.synchronize(id);
    }

    private void collect(Set<Long> ids, List<CanalEntry.Column> columns) {
        for (CanalEntry.Column column : columns) {
            if ("id".equals(column.getName()) && !column.getIsNull()) ids.add(Long.valueOf(column.getValue()));
        }
    }
}
