/*
 * Copyright (C) 2020 Dremio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dremio.nessie.iceberg;

import static com.dremio.nessie.client.NessieConfigConstants.CONF_NESSIE_REF;

import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.dremio.nessie.error.NessieConflictException;
import com.dremio.nessie.error.NessieNotFoundException;
import com.dremio.nessie.model.Branch;

class ITTestCatalogBranch extends BaseTestIceberg {

  public ITTestCatalogBranch() {
    super("main");
  }

  @SuppressWarnings("VariableDeclarationUsageDistance")
  @Test
  public void testBasicBranch() throws NessieNotFoundException, NessieConflictException {
    TableIdentifier foobar = TableIdentifier.of("foo", "bar");
    TableIdentifier foobaz = TableIdentifier.of("foo", "baz");
    Table bar = createTable(foobar, 1); //table 1
    createTable(foobaz, 1); //table 2
    catalog.refresh();
    createBranch("test", catalog.getHash());

    hadoopConfig.set(CONF_NESSIE_REF, "test");

    NessieCatalog newCatalog = new NessieCatalog(hadoopConfig);
    String initialMetadataLocation = getContent(newCatalog, foobar);
    Assertions.assertEquals(initialMetadataLocation, getContent(catalog, foobar));
    Assertions.assertEquals(getContent(newCatalog, foobaz), getContent(catalog, foobaz));
    bar.updateSchema().addColumn("id1", Types.LongType.get()).commit();

    // metadata location changed no longer matches
    Assertions.assertNotEquals(getContent(catalog, foobar), getContent(newCatalog, foobar));

    // points to the previous metadata location
    Assertions.assertEquals(initialMetadataLocation, getContent(newCatalog, foobar));
    initialMetadataLocation = getContent(newCatalog, foobaz);


    newCatalog.loadTable(foobaz).updateSchema().addColumn("id1", Types.LongType.get()).commit();

    // metadata location changed no longer matches
    Assertions.assertNotEquals(getContent(catalog, foobaz), getContent(newCatalog, foobaz));

    // points to the previous metadata location
    Assertions.assertEquals(initialMetadataLocation, getContent(catalog, foobaz));

    String mainHash = tree.getReferenceByName("main").getHash();
    tree.assignBranch("main", mainHash, Branch.of("main", newCatalog.getHash()));
    Assertions.assertEquals(getContent(newCatalog, foobar),
                            getContent(catalog, foobar));
    Assertions.assertEquals(getContent(newCatalog, foobaz),
                            getContent(catalog, foobaz));
    catalog.dropTable(foobar);
    catalog.dropTable(foobaz);
    newCatalog.refresh();
    catalog.getTreeApi().deleteBranch("test", newCatalog.getHash());
  }

}
