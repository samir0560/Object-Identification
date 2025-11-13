package com.example.demo.repository;

import com.example.demo.model.IdentificationResult;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IdentificationResultRepository extends MongoRepository<IdentificationResult, String> {
    
    List<IdentificationResult> findAllByOrderByCreatedAtDesc();
    
    List<IdentificationResult> findByUserIdOrderByCreatedAtDesc(String userId);
}

