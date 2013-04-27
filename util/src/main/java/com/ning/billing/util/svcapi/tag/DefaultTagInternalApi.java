/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.util.svcapi.tag;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import com.ning.billing.ObjectType;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.DefaultControlTag;
import com.ning.billing.util.tag.DefaultTagDefinition;
import com.ning.billing.util.tag.DescriptiveTag;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;
import com.ning.billing.util.tag.dao.TagDao;
import com.ning.billing.util.tag.dao.TagDefinitionDao;
import com.ning.billing.util.tag.dao.TagDefinitionModelDao;
import com.ning.billing.util.tag.dao.TagModelDao;
import com.ning.billing.util.tag.dao.TagModelDaoHelper;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public class DefaultTagInternalApi implements TagInternalApi {

    private final TagDao tagDao;
    private final TagDefinitionDao tagDefinitionDao;

    @Inject
    public DefaultTagInternalApi(final TagDao tagDao,
                                 final TagDefinitionDao tagDefinitionDao) {
        this.tagDao = tagDao;
        this.tagDefinitionDao = tagDefinitionDao;
    }

    @Override
    public List<TagDefinition> getTagDefinitions(final InternalTenantContext context) {
        return ImmutableList.<TagDefinition>copyOf(Collections2.transform(tagDefinitionDao.getTagDefinitions(context),
                                                                          new Function<TagDefinitionModelDao, TagDefinition>() {
                                                                              @Override
                                                                              public TagDefinition apply(final TagDefinitionModelDao input) {
                                                                                  return new DefaultTagDefinition(input, TagModelDaoHelper.isControlTag(input.getName()));
                                                                              }
                                                                          }));
    }

    @Override
    public List<Tag> getTags(final UUID objectId, final ObjectType objectType, final InternalTenantContext context) {
        return ImmutableList.<Tag>copyOf(Collections2.transform(tagDao.getTagsForObject(objectId, objectType, context),
                                                                new Function<TagModelDao, Tag>() {
                                                                    @Override
                                                                    public Tag apply(final TagModelDao input) {
                                                                        return TagModelDaoHelper.isControlTag(input.getTagDefinitionId()) ?
                                                                               new DefaultControlTag(ControlTagType.getTypeFromId(input.getTagDefinitionId()), objectType, objectId, input.getCreatedDate()) :
                                                                               new DescriptiveTag(input.getTagDefinitionId(), objectType, objectId, input.getCreatedDate());
                                                                    }
                                                                }));
    }

    @Override
    public void addTag(final UUID objectId, final ObjectType objectType, final UUID tagDefinitionId, final InternalCallContext context)
            throws TagApiException {
        final TagModelDao tag = new TagModelDao(context.getCreatedDate(), tagDefinitionId, objectId, objectType);
        tagDao.create(tag, context);

    }

    @Override
    public void removeTag(final UUID objectId, final ObjectType objectType, final UUID tagDefinitionId, final InternalCallContext context)
            throws TagApiException {
        tagDao.deleteTag(objectId, objectType, tagDefinitionId, context);
    }
}
