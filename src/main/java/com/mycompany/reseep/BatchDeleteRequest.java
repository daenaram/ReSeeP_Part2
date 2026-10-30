package com.mycompany.reseep;

import java.util.List;

public class BatchDeleteRequest {
    private List<Long> ids;
    public BatchDeleteRequest() {}
    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }
}