/*
**        DroidPlugin Project
**
** Copyright(c) 2015 Andy Zhang <zhangyong232@gmail.com>
**
** This file is part of DroidPlugin.
**
** DroidPlugin is free software: you can redistribute it and/or
** modify it under the terms of the GNU Lesser General Public
** License as published by the Free Software Foundation, either
** version 3 of the License, or (at your option) any later version.
**
** DroidPlugin is distributed in the hope that it will be useful,
** but WITHOUT ANY WARRANTY; without even the implied warranty of
** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
** Lesser General Public License for more details.
**
** You should have received a copy of the GNU Lesser General Public
**
**/

package com.morgoo.droidplugin.stub;

import android.app.Activity;
import android.content.res.Configuration;

/**
 * Created by Andy Zhang(zhangyong232@gmail.com) on 2015/2/9.
 */
public abstract class ActivityStub extends Activity {

    private static class SingleInstanceStub extends ActivityStub {
    }

    private static class SingleTaskStub extends ActivityStub {
    }

    private static class SingleTopStub extends ActivityStub {
    }

    private static class StandardStub extends ActivityStub {
    }


    public static class P00{

        public static class SingleInstance00 extends SingleInstanceStub {
        }

        public static class SingleTask00 extends SingleTaskStub {
        }

        public static class SingleTop00 extends SingleTopStub {
        }

        public static class SingleInstance01 extends SingleInstanceStub {
        }

        public static class SingleTask01 extends SingleTaskStub {
        }

        public static class SingleTop01 extends SingleTopStub {
        }

        public static class SingleInstance02 extends SingleInstanceStub {
        }

        public static class SingleTask02 extends SingleTaskStub {
        }

        public static class SingleTop02 extends SingleTopStub {
        }

        public static class SingleInstance03 extends SingleInstanceStub {
        }

        public static class SingleTask03 extends SingleTaskStub {
        }

        public static class SingleTop03 extends SingleTopStub {
        }

        public static class Standard00 extends StandardStub {
        }
    }

    public static class Dialog {

        public static class P00{

            public static class SingleInstance00 extends SingleInstanceStub {
            }

            public static class SingleTask00 extends SingleTaskStub {
            }

            public static class SingleTop00 extends SingleTopStub {
            }

            public static class SingleInstance01 extends SingleInstanceStub {
            }

            public static class SingleTask01 extends SingleTaskStub {
            }

            public static class SingleTop01 extends SingleTopStub {
            }

            public static class SingleInstance02 extends SingleInstanceStub {
            }

            public static class SingleTask02 extends SingleTaskStub {
            }

            public static class SingleTop02 extends SingleTopStub {
            }

            public static class SingleInstance03 extends SingleInstanceStub {
            }

            public static class SingleTask03 extends SingleTaskStub {
            }

            public static class SingleTop03 extends SingleTopStub {
            }

            public static class Standard00 extends StandardStub {
            }
        }
    }
}
