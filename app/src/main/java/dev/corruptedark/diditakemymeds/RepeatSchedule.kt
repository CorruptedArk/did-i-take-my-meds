package dev.corruptedark.diditakemymeds

data class RepeatSchedule(var hour: Int,
                          var minute: Int,
                          var startDay: Int,
                          var startMonth: Int,
                          val startYear: Int,
                          var daysBetween: Int = 1,
                          var weeksBetween: Int = 0,
                          var monthsBetween: Int = 0,
                          var yearsBetween: Int = 0)
