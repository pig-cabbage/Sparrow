package com.micerlab.sparrow.dao.es;

import com.alibaba.fastjson.JSONObject;
import com.micerlab.sparrow.domain.ErrorCode;
import com.micerlab.sparrow.domain.file.SpaFile;
import com.micerlab.sparrow.domain.search.SpaFilter;
import com.micerlab.sparrow.domain.search.SpaFilterType;
import com.micerlab.sparrow.utils.BusinessException;
import org.elasticsearch.action.get.*;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Component
public class SpaFileDao
{
    private Logger logger = LoggerFactory.getLogger(SpaFileDao.class);
    
    @Autowired
    private RestHighLevelClient restHighLevelClient;
    
    @Autowired
    private ElasticsearchBaseDao elasticsearchBaseDao;
    
    private final static String index = SparrowIndex.SPA_FILES.getIndex();
    
    public String getDocId(String file_id)
    {
        return getFileMeta(file_id).getDoc_id();
    }
    
    public SpaFile getFileMeta(String file_id)
    {
        Map<String, Object> fileMeta = elasticsearchBaseDao.getESDoc(index, file_id);
        if (fileMeta == null)
            throw new BusinessException(ErrorCode.NOT_FOUND_FILE_ID, file_id);
        JSONObject jsonObject = new JSONObject(fileMeta);
        SpaFile spaFile = jsonObject.toJavaObject(SpaFile.class);
        return spaFile;
    }
    
    public void updateFileMeta(String file_id, Map<String, Object> srcMap)
    {
        elasticsearchBaseDao.updateESDoc(index, file_id, srcMap);
    }
    
    public void deleteFileMeta(String file_id)
    {
        elasticsearchBaseDao.deleteESDoc(index, file_id);
    }
    
    public void createFileMeta(String file_id, Map<String, Object> srcMap)
    {
        elasticsearchBaseDao.indexESDoc(index, file_id, srcMap);
    }
    
    public List<SpaFilter> retrieveFileSpaFilters(String file_id, SpaFilterType spaFilterType)
    {
        List<Map<String, Object>> spaFilterMaps = elasticsearchBaseDao.termsLookup(
                spaFilterType.sparrowIndex().getIndex(),
                index, file_id, spaFilterType.getTypes());
        List<SpaFilter> spaFilters = new LinkedList<>();
        for (Map<String, Object> spaFilterMap : spaFilterMaps)
        {
            SpaFilter spaFilter = (new JSONObject(spaFilterMap)).toJavaObject(SpaFilter.class);
            spaFilters.add(spaFilter);
        }
        return spaFilters;
    }
    
    public void updateFileSpaFilters(String file_id, SpaFilterType spaFilterType, List<Long> spaFilterIds)
    {
        Map<String, Object> docMap = new HashMap<>();
        docMap.put(spaFilterType.getTypes(), spaFilterIds);
        elasticsearchBaseDao.updateESDoc(index, file_id, docMap);
    }
    
    public void updateFileThumbnail(String file_id, String thumbnail)
    {
        Map<String, Object> docMap = new HashMap<>();
        docMap.put("thumbnail", thumbnail);
        elasticsearchBaseDao.updateESDoc(index, file_id, docMap);
    }
    
    public Map<String, Object> getDocAndParentFile(String doc_id, String parent_id)
    {
        Map<String, Object> result = new HashMap<>();
        if(StringUtils.isEmpty(parent_id))
        {
            result.put("parent", null);
            result.put("doc", elasticsearchBaseDao.getESDoc(SparrowIndex.SPA_DOCS.getIndex(), doc_id));
        }
        else {
            MultiGetRequest multiGetRequest = new MultiGetRequest();
            multiGetRequest.add(SparrowIndex.SPA_DOCS.getIndex(), doc_id);
            multiGetRequest.add(SparrowIndex.SPA_FILES.getIndex(), parent_id);
    
            try
            {
                MultiGetResponse multiGetResponse = restHighLevelClient.mget(multiGetRequest, RequestOptions.DEFAULT);
                MultiGetItemResponse[] itemResponses = multiGetResponse.getResponses();
                result.put("doc", itemResponses[0].getResponse().getSourceAsMap());
                result.put("parent", itemResponses[1].getResponse().getSource());
            } catch (IOException ex)
            {
                logger.error(ex.getMessage());
                ex.printStackTrace();
                throw new BusinessException(ErrorCode.SERVER_ERR_ELASTICSEARCH, ex.getMessage());
            }
        }
        return result;
    }
    
    
}
