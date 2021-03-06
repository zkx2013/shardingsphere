/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.shadow.route.engine;

import org.apache.shardingsphere.core.rule.ShadowRule;
import org.apache.shardingsphere.shadow.route.engine.impl.PreparedShadowDataSourceRouter;
import org.apache.shardingsphere.shadow.route.engine.impl.SimpleShadowDataSourceRouter;
import org.apache.shardingsphere.sql.parser.binder.statement.SQLStatementContext;
import org.apache.shardingsphere.sql.parser.sql.statement.SQLStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.dml.DMLStatement;
import org.apache.shardingsphere.underlying.common.config.properties.ConfigurationProperties;
import org.apache.shardingsphere.underlying.common.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.underlying.route.context.RouteContext;
import org.apache.shardingsphere.underlying.route.context.RouteMapper;
import org.apache.shardingsphere.underlying.route.context.RouteResult;
import org.apache.shardingsphere.underlying.route.context.RouteUnit;
import org.apache.shardingsphere.underlying.route.decorator.RouteDecorator;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Route decorator for shadow.
 */
public final class ShadowRouteDecorator implements RouteDecorator<ShadowRule> {
    
    @Override
    public RouteContext decorate(final RouteContext routeContext, final ShardingSphereMetaData metaData, final ShadowRule shadowRule, final ConfigurationProperties properties) {
        return routeContext.getRouteResult().getRouteUnits().isEmpty() ? getRouteContext(routeContext, shadowRule) : getRouteContextWithRouteResult(routeContext, shadowRule);
    }
    
    private RouteContext getRouteContext(final RouteContext routeContext, final ShadowRule shadowRule) {
        SQLStatementContext sqlStatementContext = routeContext.getSqlStatementContext();
        SQLStatement sqlStatement = sqlStatementContext.getSqlStatement();
        RouteResult routeResult = new RouteResult();
        List<Object> parameters = routeContext.getParameters();
        if (!(sqlStatement instanceof DMLStatement)) {
            shadowRule.getShadowMappings().forEach((k, v) -> {
                routeResult.getRouteUnits().add(new RouteUnit(new RouteMapper(k, k), Collections.emptyList()));
                routeResult.getRouteUnits().add(new RouteUnit(new RouteMapper(v, v), Collections.emptyList()));
            });
            return new RouteContext(sqlStatementContext, parameters, routeResult);
        }
        if (isShadowSQL(routeContext, shadowRule)) {
            shadowRule.getShadowMappings().keySet().forEach(each -> routeResult.getRouteUnits().add(new RouteUnit(new RouteMapper(each, each), Collections.emptyList())));
        } else {
            shadowRule.getShadowMappings().values().forEach(each -> routeResult.getRouteUnits().add(new RouteUnit(new RouteMapper(each, each), Collections.emptyList())));
        }
        return new RouteContext(sqlStatementContext, parameters, routeResult);
    }
    
    private RouteContext getRouteContextWithRouteResult(final RouteContext routeContext, final ShadowRule shadowRule) {
        SQLStatement sqlStatement = routeContext.getSqlStatementContext().getSqlStatement();
        Collection<RouteUnit> toBeAdded = new LinkedList<>();
        if (!(sqlStatement instanceof DMLStatement)) {
            for (RouteUnit each : routeContext.getRouteResult().getRouteUnits()) {
                String shadowDataSourceName = shadowRule.getShadowMappings().get(each.getDataSourceMapper().getActualName());
                toBeAdded.add(new RouteUnit(new RouteMapper(each.getDataSourceMapper().getLogicName(), shadowDataSourceName), Collections.emptyList()));
            }
            routeContext.getRouteResult().getRouteUnits().addAll(toBeAdded);
            return routeContext;
        }
        if (isShadowSQL(routeContext, shadowRule)) {
            for (RouteUnit each : routeContext.getRouteResult().getRouteUnits()) {
                routeContext.getRouteResult().getRouteUnits().remove(each);
                String shadowDataSourceName = shadowRule.getShadowMappings().get(each.getDataSourceMapper().getActualName());
                routeContext.getRouteResult().getRouteUnits().add(new RouteUnit(new RouteMapper(each.getDataSourceMapper().getLogicName(), shadowDataSourceName), Collections.emptyList()));
            }
        }
        return routeContext;
    }
    
    private boolean isShadowSQL(final RouteContext routeContext, final ShadowRule shadowRule) {
        List<Object> parameters = routeContext.getParameters();
        SQLStatementContext sqlStatementContext = routeContext.getSqlStatementContext();
        ShadowDataSourceRouter shadowDataSourceRouter = parameters == null ? new SimpleShadowDataSourceRouter(shadowRule, sqlStatementContext)
                : new PreparedShadowDataSourceRouter(shadowRule, sqlStatementContext, parameters);
        return shadowDataSourceRouter.isShadowSQL();
    }
    
    @Override
    public int getOrder() {
        return 20;
    }
    
    @Override
    public Class<ShadowRule> getTypeClass() {
        return ShadowRule.class;
    }
}
