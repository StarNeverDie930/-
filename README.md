# 分布式多车协作探索系统

## 项目介绍
这是一个基于分布式架构的多车协作路径规划与探索系统，包含以下核心组件：
- 控制器(controller)：负责任务调度和指令分发
- 导航器(navigator)：实现A*和D* Lite路径规划算法
- 任务评判器(carTaskJudge)：负责任务评估和状态监控
- 可视化界面(carAndView)：提供实时路径展示和交互界面

## 主要功能
1. 多车分布式协作探索
2. 动态路径规划与优化
3. 实时障碍物检测与避障
4. 任务状态监控与评估
5. 路径可视化展示

## 技术架构
- 后端：Java Spring Boot
- 通信：RabbitMQ消息队列
- 数据存储：Redis缓存
- 算法：A*和D* Lite路径规划

## 快速开始
1. 安装依赖：
```
mvn clean install
```
2. 启动Redis和RabbitMQ服务
3. 依次启动各组件：
```
cd controller && mvn spring-boot:run
cd navigator && mvn spring-boot:run
cd carTaskJudge && mvn spring-boot:run
cd carAndView && mvn spring-boot:run
```

## 项目结构
```
car_find_way2 - final-1/
├── carAndView/      # 可视化界面
├── controller/      # 控制中心
├── navigator/       # 路径规划
├── carTaskJudge/    # 任务评判
└── docs/            # 设计文档
```

## 许可证
MIT License