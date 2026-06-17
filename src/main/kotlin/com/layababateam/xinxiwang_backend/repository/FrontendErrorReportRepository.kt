package com.layababateam.xinxiwang_backend.repository

import com.layababateam.xinxiwang_backend.model.FrontendErrorReport
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface FrontendErrorReportRepository : MongoRepository<FrontendErrorReport, String>
