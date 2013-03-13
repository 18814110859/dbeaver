/*
 * Copyright (C) 2010-2012 Serge Rieder
 * serge@jkiss.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.jkiss.dbeaver.tools.transfer.database;

import org.eclipse.swt.graphics.Image;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.ui.IObjectImageProvider;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPDataTypeProvider;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.*;
import org.jkiss.dbeaver.ui.DBIcon;
import org.jkiss.utils.CommonUtils;

import java.util.ArrayList;
import java.util.List;

/**
* DatabaseMappingAttribute
*/
class DatabaseMappingAttribute implements DatabaseMappingObject {
    public static final String DEFAULT_TARGET_TYPE = "VARCHAR";
    final DatabaseMappingContainer parent;
    DBSAttributeBase source;
    DBSEntityAttribute target;
    String targetName;
    String targetType;
    DatabaseMappingType mappingType;

    DatabaseMappingAttribute(DatabaseMappingContainer parent, DBSAttributeBase source)
    {
        this.parent = parent;
        this.source = source;
        this.mappingType = DatabaseMappingType.unspecified;
    }

    public DatabaseMappingContainer getParent()
    {
        return parent;
    }

    @Override
    public Image getIcon()
    {
        if (source instanceof IObjectImageProvider) {
            return ((IObjectImageProvider) source).getObjectImage();
        }
        return DBIcon.TREE_COLUMN.getImage();
    }

    @Override
    public DBSAttributeBase getSource()
    {
        return source;
    }

    @Override
    public String getTargetName()
    {
        switch (mappingType) {
            case existing: return DBUtils.getObjectFullName(target);
            case create: return targetName;
            case skip: return "[skip]";
            default: return "?";
        }
    }

    @Override
    public DatabaseMappingType getMappingType()
    {
        return mappingType;
    }

    public void setMappingType(DatabaseMappingType mappingType)
    {
        this.mappingType = mappingType;
        switch (mappingType) {
            case create:
                targetName = getSource().getName();
                break;
        }
    }

    void updateMappingType(DBRProgressMonitor monitor) throws DBException
    {
        switch (parent.getMappingType()) {
            case existing:
            {
                mappingType = DatabaseMappingType.unspecified;
                if (parent.getTarget() instanceof DBSEntity) {
                    target = ((DBSEntity) parent.getTarget()).getAttribute(monitor, source.getName());
                    if (target != null) {
                        mappingType = DatabaseMappingType.existing;
                    } else {
                        if (CommonUtils.isEmpty(targetName)) {
                            targetName = source.getName();
                        }
                        mappingType = DatabaseMappingType.create;
                    }
                }
                break;
            }
            case create:
                mappingType = DatabaseMappingType.create;
                if (CommonUtils.isEmpty(targetName)) {
                    targetName = source.getName();
                }
                break;
            case skip:
                mappingType = DatabaseMappingType.skip;
                break;
            default:
                mappingType = DatabaseMappingType.unspecified;
                break;
        }
    }

    public DBSEntityAttribute getTarget()
    {
        return target;
    }

    public void setTarget(DBSEntityAttribute target)
    {
        this.target = target;
    }

    public void setTargetName(String targetName)
    {
        this.targetName = targetName;
    }

    public String getTargetType(DBPDataSource targetDataSource)
    {
        if (!CommonUtils.isEmpty(targetType)) {
            return targetType;
        }
        // TODO: make some smart data type matcher
        // Current solution looks like hack
        String typeName = source.getTypeName();
        if (targetDataSource instanceof DBPDataTypeProvider) {
            DBPDataTypeProvider dataTypeProvider = (DBPDataTypeProvider) targetDataSource;
            DBSDataType dataType = dataTypeProvider.getDataType(typeName);
            if (dataType == null && typeName.equals("DOUBLE")) {
                dataType = dataTypeProvider.getDataType("DOUBLE PRECISION");
                if (dataType != null) {
                    typeName = dataType.getTypeName();
                }
            }
            if (dataType == null) {
                // Type not supported by target database
                // Let's try to find something similar
                List<DBSDataType> possibleTypes = new ArrayList<DBSDataType>();
                for (DBSDataType type : dataTypeProvider.getDataTypes()) {
                    if (type.getDataKind() == source.getDataKind()) {
                        possibleTypes.add(type);
                    }
                }
                if (possibleTypes.isEmpty()) {
                    // Not supported by target database
                    typeName = source.getDataKind().getDefaultTypeName();
                } else {
                    DBSDataType targetType = null;
                    for (DBSDataType type : possibleTypes) {
                        if (type.getName().equals(source.getDataKind().getDefaultTypeName()) ||
                            type.getPrecision() == source.getPrecision()) {
                            targetType = type;
                            break;
                        }
                    }
                    if (targetType == null) {
                        targetType = possibleTypes.get(0);
                    }
                    typeName = targetType.getTypeName();
                }
            }
        }

        if (source.getDataKind() == DBSDataKind.STRING) {
            typeName += "(" + source.getMaxLength() + ")";
        }
        return typeName;
    }

    public void setTargetType(String targetType)
    {
        this.targetType = targetType;
    }
}
