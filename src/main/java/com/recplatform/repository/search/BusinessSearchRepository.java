package com.recplatform.repository.search;

import com.recplatform.model.BusinessDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface BusinessSearchRepository extends ElasticsearchRepository<BusinessDocument, String> {

    List<BusinessDocument> findByCategoriesContaining(String category);
}
