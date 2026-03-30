#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
FastAPI 主应用入口
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import os
from dotenv import load_dotenv
from app.api.v1.maintenance import router as maintenance_router
from app.api.v1.replenishment import router as replenishment_router
from app.api.v1.jobs import router as jobs_router

# 加载环境变量
load_dotenv()

# 创建 FastAPI 应用
app = FastAPI(
    title="Spare Parts AI Service",
    description="备件管理系统 AI 微服务",
    version="1.0.0"
)

allow_origins = [
    origin.strip()
    for origin in os.getenv("CORS_ALLOW_ORIGINS", "http://localhost:3000,http://localhost:8080").split(",")
    if origin.strip()
]

# 添加 CORS 中间件（允许 Java 后端跨域访问）
app.add_middleware(
    CORSMiddleware,
    allow_origins=allow_origins,
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ==================== 健康检查端点 ====================
@app.get("/health")
def health_check():
    """健康检查端点，用于 Java 后端和负载均衡器"""
    return {
        "status": "ok",
        "service": "spare-parts-ai-service",
        "version": "1.0.0"
    }

@app.get("/")
def root():
    """根路径"""
    return {
        "message": "欢迎使用备件管理系统 AI 微服务",
        "docs": "/docs"
    }

# ==================== API 路由（后续添加）====================
app.include_router(maintenance_router)
app.include_router(replenishment_router)
app.include_router(jobs_router)

if __name__ == "__main__":
    import uvicorn
    
    port = int(os.getenv("API_PORT", 8001))
    host = os.getenv("API_HOST", "0.0.0.0")
    
    print(f"🚀 启动 FastAPI 服务: {host}:{port}")
    print(f"📖 API 文档: http://localhost:{port}/docs")
    
    uvicorn.run(
        app,
        host=host,
        port=port,
        reload=os.getenv("FASTAPI_ENV") == "development"
    )
